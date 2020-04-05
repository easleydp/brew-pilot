export default function applyPatchRangeDefaultLeft(Highcharts, rangeDefaultLeft) {
  /* Based on highstock.src.js 7.2.1 */

  (function() {
    var H = Highcharts;
    var correctFloat = H.correctFloat;
    var Axis = Highcharts.Axis;

    var splat = Highcharts.splat;
    var addEvent = Highcharts.addEvent;
    var isNumber = Highcharts.isNumber;
    var pick = Highcharts.pick;
    var defined = Highcharts.defined;
    var fireEvent = Highcharts.fireEvent;
    var normalizeTickInterval = Highcharts.normalizeTickInterval;
    var getMagnitude = Highcharts.getMagnitude;

    //window.Highcharts.Axis.prototype.setTickInterval = function(secondPass) {
    Highcharts.Axis.prototype.setTickInterval = function(secondPass) {
      var axis = this,
        chart = axis.chart,
        options = axis.options,
        isLog = axis.isLog,
        isDatetimeAxis = axis.isDatetimeAxis,
        isXAxis = axis.isXAxis,
        isLinked = axis.isLinked,
        maxPadding = options.maxPadding,
        minPadding = options.minPadding,
        length,
        linkedParentExtremes,
        tickIntervalOption = options.tickInterval,
        minTickInterval,
        tickPixelIntervalOption = options.tickPixelInterval,
        categories = axis.categories,
        threshold = isNumber(axis.threshold) ? axis.threshold : null,
        softThreshold = axis.softThreshold,
        thresholdMin,
        thresholdMax,
        hardMin,
        hardMax;
      if (!isDatetimeAxis && !categories && !isLinked) {
        this.getTickAmount();
      }
      // Min or max set either by zooming/setExtremes or initial options
      hardMin = pick(axis.userMin, options.min);
      hardMax = pick(axis.userMax, options.max);
      // Linked axis gets the extremes from the parent axis
      if (isLinked) {
        axis.linkedParent = chart[axis.coll][options.linkedTo];
        linkedParentExtremes = axis.linkedParent.getExtremes();
        axis.min = pick(linkedParentExtremes.min, linkedParentExtremes.dataMin);
        axis.max = pick(linkedParentExtremes.max, linkedParentExtremes.dataMax);
        if (options.type !== axis.linkedParent.options.type) {
          // Can't link axes of different type
          H.error(11, 1, chart);
        }
        // Initial min and max from the extreme data values
      } else {
        // Adjust to hard threshold
        if (!softThreshold && defined(threshold)) {
          if (axis.dataMin >= threshold) {
            thresholdMin = threshold;
            minPadding = 0;
          } else if (axis.dataMax <= threshold) {
            thresholdMax = threshold;
            maxPadding = 0;
          }
        }
        axis.min = pick(hardMin, thresholdMin, axis.dataMin);
        axis.max = pick(hardMax, thresholdMax, axis.dataMax);
      }
      if (isLog) {
        if (
          axis.positiveValuesOnly &&
          !secondPass &&
          Math.min(axis.min, pick(axis.dataMin, axis.min)) <= 0
        ) {
          // #978
          // Can't plot negative values on log axis
          H.error(10, 1, chart);
        }
        // The correctFloat cures #934, float errors on full tens. But it
        // was too aggressive for #4360 because of conversion back to lin,
        // therefore use precision 15.
        axis.min = correctFloat(axis.log2lin(axis.min), 16);
        axis.max = correctFloat(axis.log2lin(axis.max), 16);
      }
      // handle zoomed range
      // HACK
      if (rangeDefaultLeft) {
        if (axis.range && defined(axis.min)) {
          // #618, #6773:
          axis.userMin = axis.min = hardMin = axis.dataMin;
          axis.userMax = hardMax = axis.userMin + axis.range;
          axis.range = null; // don't use it when running setExtremes
        }
      } else {
        // Original:
        if (axis.range && defined(axis.max)) {
          // #618, #6773:
          axis.userMin = axis.min = hardMin = Math.max(axis.dataMin, axis.minFromRange());
          axis.userMax = hardMax = axis.max;
          axis.range = null; // don't use it when running setExtremes
        }
      }
      // Hook for Highstock Scroller. Consider combining with beforePadding.
      fireEvent(axis, 'foundExtremes');
      // Hook for adjusting this.min and this.max. Used by bubble series.
      if (axis.beforePadding) {
        axis.beforePadding();
      }
      // adjust min and max for the minimum range
      axis.adjustForMinRange();
      // Pad the values to get clear of the chart's edges. To avoid
      // tickInterval taking the padding into account, we do this after
      // computing tick interval (#1337).
      if (
        !categories &&
        !axis.axisPointRange &&
        !axis.usePercentage &&
        !isLinked &&
        defined(axis.min) &&
        defined(axis.max)
      ) {
        length = axis.max - axis.min;
        if (length) {
          if (!defined(hardMin) && minPadding) {
            axis.min -= length * minPadding;
          }
          if (!defined(hardMax) && maxPadding) {
            axis.max += length * maxPadding;
          }
        }
      }
      // Handle options for floor, ceiling, softMin and softMax (#6359)
      if (isNumber(options.softMin) && !isNumber(axis.userMin) && options.softMin < axis.min) {
        axis.min = hardMin = options.softMin; // #6894
      }
      if (isNumber(options.softMax) && !isNumber(axis.userMax) && options.softMax > axis.max) {
        axis.max = hardMax = options.softMax; // #6894
      }
      if (isNumber(options.floor)) {
        axis.min = Math.min(Math.max(axis.min, options.floor), Number.MAX_VALUE);
      }
      if (isNumber(options.ceiling)) {
        axis.max = Math.max(
          Math.min(axis.max, options.ceiling),
          pick(axis.userMax, -Number.MAX_VALUE)
        );
      }
      // When the threshold is soft, adjust the extreme value only if the data
      // extreme and the padded extreme land on either side of the threshold.
      // For example, a series of [0, 1, 2, 3] would make the yAxis add a tick
      // for -1 because of the default minPadding and startOnTick options.
      // This is prevented by the softThreshold option.
      if (softThreshold && defined(axis.dataMin)) {
        threshold = threshold || 0;
        if (!defined(hardMin) && axis.min < threshold && axis.dataMin >= threshold) {
          axis.min = axis.options.minRange
            ? Math.min(threshold, axis.max - axis.minRange)
            : threshold;
        } else if (!defined(hardMax) && axis.max > threshold && axis.dataMax <= threshold) {
          axis.max = axis.options.minRange
            ? Math.max(threshold, axis.min + axis.minRange)
            : threshold;
        }
      }
      // get tickInterval
      if (axis.min === axis.max || axis.min === undefined || axis.max === undefined) {
        axis.tickInterval = 1;
      } else if (
        isLinked &&
        !tickIntervalOption &&
        tickPixelIntervalOption === axis.linkedParent.options.tickPixelInterval
      ) {
        axis.tickInterval = tickIntervalOption = axis.linkedParent.tickInterval;
      } else {
        axis.tickInterval = pick(
          tickIntervalOption,
          this.tickAmount ? (axis.max - axis.min) / Math.max(this.tickAmount - 1, 1) : undefined,
          // For categoried axis, 1 is default, for linear axis use
          // tickPix
          categories
            ? 1
            : // don't let it be more than the data range
              ((axis.max - axis.min) * tickPixelIntervalOption) /
                Math.max(axis.len, tickPixelIntervalOption)
        );
      }
      // Now we're finished detecting min and max, crop and group series data.
      // This is in turn needed in order to find tick positions in ordinal
      // axes.
      if (isXAxis && !secondPass) {
        axis.series.forEach(function(series) {
          series.processData(axis.min !== axis.oldMin || axis.max !== axis.oldMax);
        });
      }
      // set the translation factor used in translate function
      axis.setAxisTranslation(true);
      // hook for ordinal axes and radial axes
      if (axis.beforeSetTickPositions) {
        axis.beforeSetTickPositions();
      }
      // hook for extensions, used in Highstock ordinal axes
      if (axis.postProcessTickInterval) {
        axis.tickInterval = axis.postProcessTickInterval(axis.tickInterval);
      }
      // In column-like charts, don't cramp in more ticks than there are
      // points (#1943, #4184)
      if (axis.pointRange && !tickIntervalOption) {
        axis.tickInterval = Math.max(axis.pointRange, axis.tickInterval);
      }
      // Before normalizing the tick interval, handle minimum tick interval.
      // This applies only if tickInterval is not defined.
      minTickInterval = pick(
        options.minTickInterval,
        axis.isDatetimeAxis && axis.closestPointRange
      );
      if (!tickIntervalOption && axis.tickInterval < minTickInterval) {
        axis.tickInterval = minTickInterval;
      }
      // for linear axes, get magnitude and normalize the interval
      if (!isDatetimeAxis && !isLog && !tickIntervalOption) {
        axis.tickInterval = normalizeTickInterval(
          axis.tickInterval,
          null,
          getMagnitude(axis.tickInterval),
          // If the tick interval is between 0.5 and 5 and the axis max is
          // in the order of thousands, chances are we are dealing with
          // years. Don't allow decimals. #3363.
          pick(
            options.allowDecimals,
            !(
              axis.tickInterval > 0.5 &&
              axis.tickInterval < 5 &&
              axis.max > 1000 &&
              axis.max < 9999
            )
          ),
          !!this.tickAmount
        );
      }
      // Prevent ticks from getting so close that we can't draw the labels
      if (!this.tickAmount) {
        axis.tickInterval = axis.unsquish();
      }
      this.setTickPositions();
    };

    //window.Highcharts.RangeSelector.prototype.clickButton = function(i, redraw) {
    Highcharts.RangeSelector.prototype.clickButton = function(i, redraw) {
      var rangeSelector = this,
        chart = rangeSelector.chart,
        rangeOptions = rangeSelector.buttonOptions[i],
        baseAxis = chart.xAxis[0],
        unionExtremes = (chart.scroller && chart.scroller.getUnionExtremes()) || baseAxis || {},
        dataMin = unionExtremes.dataMin,
        dataMax = unionExtremes.dataMax,
        // HACK
        //                    newMin,
        newMin = rangeDefaultLeft
          ? baseAxis && Math.round(Math.max(baseAxis.min, pick(dataMin, baseAxis.min)))
          : undefined,
        newMax = baseAxis && Math.round(Math.min(baseAxis.max, pick(dataMax, baseAxis.max))), // #1568
        type = rangeOptions.type,
        baseXAxisOptions,
        range = rangeOptions._range,
        rangeMin,
        minSetting,
        rangeSetting,
        ctx,
        ytdExtremes,
        dataGrouping = rangeOptions.dataGrouping;

      // chart has no data, base series is removed
      if (dataMin === null || dataMax === null) {
        return;
      }
      // Set the fixed range before range is altered
      chart.fixedRange = range;
      // Apply dataGrouping associated to button
      if (dataGrouping) {
        this.forcedDataGrouping = true;
        Axis.prototype.setDataGrouping.call(baseAxis || { chart: this.chart }, dataGrouping, false);
        this.frozenStates = rangeOptions.preserveDataGrouping;
      }
      // Apply range
      if (type === 'month' || type === 'year') {
        if (!baseAxis) {
          // This is set to the user options and picked up later when the
          // axis is instantiated so that we know the min and max.
          range = rangeOptions;
        } else {
          ctx = {
            range: rangeOptions,
            max: newMax,
            chart: chart,
            dataMin: dataMin,
            dataMax: dataMax,
          };
          newMin = baseAxis.minFromRange.call(ctx);
          if (isNumber(ctx.newMax)) {
            newMax = ctx.newMax;
          }
        }
        // Fixed times like minutes, hours, days
      } else if (range) {
        // HACK
        if (rangeDefaultLeft) {
          newMax = Math.min(newMin + range, dataMax);
          newMin = Math.max(newMax - range, dataMin);
        } else {
          // Original:
          newMin = Math.max(newMax - range, dataMin);
          newMax = Math.min(newMin + range, dataMax);
        }
      } else if (type === 'ytd') {
        // On user clicks on the buttons, or a delayed action running from
        // the beforeRender event (below), the baseAxis is defined.
        if (baseAxis) {
          // When "ytd" is the pre-selected button for the initial view,
          // its calculation is delayed and rerun in the beforeRender
          // event (below). When the series are initialized, but before
          // the chart is rendered, we have access to the xData array
          // (#942).
          if (dataMax === undefined) {
            dataMin = Number.MAX_VALUE;
            dataMax = Number.MIN_VALUE;
            chart.series.forEach(function(series) {
              // reassign it to the last item
              var xData = series.xData;
              dataMin = Math.min(xData[0], dataMin);
              dataMax = Math.max(xData[xData.length - 1], dataMax);
            });
            redraw = false;
          }
          ytdExtremes = rangeSelector.getYTDExtremes(dataMax, dataMin, chart.time.useUTC);
          newMin = rangeMin = ytdExtremes.min;
          newMax = ytdExtremes.max;
          // "ytd" is pre-selected. We don't yet have access to processed
          // point and extremes data (things like pointStart and pointInterval
          // are missing), so we delay the process (#942)
        } else {
          rangeSelector.deferredYTDClick = i;
          return;
        }
      } else if (type === 'all' && baseAxis) {
        newMin = dataMin;
        newMax = dataMax;
      }
      newMin += rangeOptions._offsetMin;
      newMax += rangeOptions._offsetMax;
      rangeSelector.setSelected(i);
      // Update the chart
      if (!baseAxis) {
        // Axis not yet instanciated. Temporarily set min and range
        // options and remove them on chart load (#4317).
        baseXAxisOptions = splat(chart.options.xAxis)[0];
        rangeSetting = baseXAxisOptions.range;
        baseXAxisOptions.range = range;
        minSetting = baseXAxisOptions.min;
        baseXAxisOptions.min = rangeMin;
        addEvent(chart, 'load', function resetMinAndRange() {
          baseXAxisOptions.range = rangeSetting;
          baseXAxisOptions.min = minSetting;
        });
      } else {
        // Existing axis object. Set extremes after render time.
        baseAxis.setExtremes(
          newMin,
          newMax,
          pick(redraw, 1),
          null, // auto animation
          {
            trigger: 'rangeSelectorButton',
            rangeSelectorButton: rangeOptions,
          }
        );
      }
    };
  })();
}
