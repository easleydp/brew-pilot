enum JumpDirection {
  ASC = 1,
  DESC,
}

export default class SpikeDetector {
  private readonly spikeDetectionThreshold: number;
  private readonly spikeReturnToPriorTolerance: number;
  private readonly readingsPeriod: number;
  private readonly spikeMaxPeriods: number;
  private readonly spikeMaxDuration: number;

  constructor({
    // A jump from one y value to another must be at least this big to be considered as possibly being one side of a spike
    spikeDetectionThreshold,
    // We don't insist that a spike returns to exactly the same prior y value
    spikeReturnToPriorTolerance,
    // Period of actual readings (note: duration between adjacent points in the series may be longer)
    readingsPeriod,
    // How wide a spike can be, in terms of number of readings periods. So for example, you might have what appears to be
    // a spike with a 2 point 'peak' but on closer inspection those 2 points could be quite distant in time (due to the
    // coalescing of points with same y value). In terms of readings periods, it's actually as if the peak consisted of
    // many more points. So it would be wrong to identify such a case as a spike.
    spikeMaxPeriods,
  }: {
    spikeDetectionThreshold: number;
    spikeReturnToPriorTolerance: number;
    readingsPeriod: number;
    spikeMaxPeriods: number;
  }) {
    this.spikeDetectionThreshold = spikeDetectionThreshold;
    this.spikeReturnToPriorTolerance = spikeReturnToPriorTolerance;
    this.readingsPeriod = readingsPeriod;
    this.spikeMaxPeriods = spikeMaxPeriods;
    this.spikeMaxDuration = readingsPeriod * spikeMaxPeriods;
  }

  /**
   * @returns array of spikes, where each spike element is an array of the constituent point indices.
   */
  detectSpikes({ xData, yData }: { xData: number[]; yData: number[] }): number[][] {
    const len = xData.length;
    if (yData.length !== len) {
      throw Error(`xData len (${xData.length}) and yData len (${yData.length}) should be same`);
    }

    const spikes: number[][] = [];
    let i = 0;

    if (len < 3) {
      return spikes;
    }

    const seekNextSpike = (): number[] | null => {
      let potentialSpike: number[] | null = null;
      let currY = yData[i];
      while (++i < len && !potentialSpike) {
        // Note, loop quits from inside if spike detected.
        let prevY = currY;
        currY = yData[i];
        const jumpDirection = this.detectJump(prevY, currY);
        if (jumpDirection) {
          // Potentially the start of a spike.
          potentialSpike = [i];
          const xSpikeStart = xData[i];
          // Prospectively push points until either we exceed spikeMaxPeriods (in which case we back up a bit then resume
          // onset detection) or y reverts to (roughly) the prior value (in which case we've found a spike and we're done).
          while (++i < len) {
            // Note: Deliberately not updating prevY here. It needs to retain the 'spike prior' value.
            currY = yData[i];
            if (xData[i] - xSpikeStart > this.spikeMaxDuration) {
              // Too wide for a spike. Back up before resuming jump detection.
              i = potentialSpike[0];
              currY = yData[i];
              potentialSpike = null;
              break;
            }
            if (this.detectReversionToPrior(jumpDirection, prevY, currY)) {
              return potentialSpike;
            }
            potentialSpike.push(i);
          }
        }
      }
      return null;
    };

    // Main loop
    while (i < len) {
      const spike = seekNextSpike(); // `i` will get incremented here
      if (spike) {
        if (spike.length === 0) {
          throw Error('Spike should consist of at least one point');
        }
        spikes.push(spike);
      }
    }
    return spikes;
  }

  private detectJump(prevVal: number, currVal: number): JumpDirection | null {
    const diff = prevVal - currVal;
    if (Math.abs(diff) < this.spikeDetectionThreshold) {
      return null;
    }
    return diff < 0 ? JumpDirection.ASC : JumpDirection.DESC;
  }

  private detectReversionToPrior(jumpDirection: JumpDirection, priorVal: number, currVal: number) {
    if (Math.abs(priorVal - currVal) <= this.spikeReturnToPriorTolerance) {
      return true;
    }
    // Alternatively, a reverting spike might completely leap-frogged the prior value.
    return jumpDirection === JumpDirection.ASC ? currVal < priorVal : currVal > priorVal;
  }
}
