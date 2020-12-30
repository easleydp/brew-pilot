/**
 * This class defines some basic methods for handling dates.
 */
export default class DateUtils {
  /**
   * Date interval constants.
   */
  static readonly MILLI = 'ms';
  static readonly SECOND = 's';
  static readonly MINUTE = 'mi';
  static readonly HOUR = 'h';
  static readonly DAY = 'd';
  static readonly MONTH = 'mo';
  static readonly YEAR = 'y';

  /**
   * The number of days in a week.
   */
  static readonly DAYS_IN_WEEK = 7;

  /**
   * The number of months in a year.
   */
  static readonly MONTHS_IN_YEAR = 12;

  /**
   * The maximum number of days in a month.
   */
  static readonly MAX_DAYS_IN_MONTH = 31;

  static readonly SUNDAY = 0;
  static readonly MONDAY = 1;
  static readonly TUESDAY = 2;
  static readonly WEDNESDAY = 3;
  static readonly THURSDAY = 4;
  static readonly FRIDAY = 5;
  static readonly SATURDAY = 6;

  /**
   * Creates and returns a new Date instance with the exact same date value as the called
   * instance. Dates are copied and passed by reference, so if a copied date variable is modified
   * later, the original variable will also be changed.  When the intention is to create a new
   * variable that will not modify the original instance, you should create a clone.
   *
   * Example of correctly cloning a date:
   *
   *     //wrong way:
   *     var orig = new Date('10/1/2006');
   *     var copy = orig;
   *     copy.setDate(5);
   *     console.log(orig);  // returns 'Thu Oct 05 2006'!
   *
   *     //correct way:
   *     var orig = new Date('10/1/2006'),
   *         copy = DateUtils.clone(orig);
   *     copy.setDate(5);
   *     console.log(orig);  // returns 'Thu Oct 01 2006'
   *
   * @param {Date} date The date.
   * @return {Date} The new Date instance.
   */
  static clone(date: Date): Date {
    return new Date(date.getTime());
  }

  /**
   * Provides a convenient method for performing basic date arithmetic. This method
   * does not modify the Date instance being called - it creates and returns
   * a new Date instance containing the resulting date value.
   *
   * Examples:
   *
   *     // Basic usage:
   *     var dt = DateUtils.add(new Date('10/29/2006'), DateUtils.DAY, 5);
   *     console.log(dt); // returns 'Fri Nov 03 2006 00:00:00'
   *
   *     // Negative values will be subtracted:
   *     var dt2 = DateUtils.add(new Date('10/1/2006'), DateUtils.DAY, -5);
   *     console.log(dt2); // returns 'Tue Sep 26 2006 00:00:00'
   *
   *      // Decimal values can be used:
   *     var dt3 = DateUtils.add(new Date('10/1/2006'), DateUtils.DAY, 1.25);
   *     console.log(dt3); // returns 'Mon Oct 02 2006 06:00:00'
   *
   * @param {Date} date The date to modify
   * @param {String} interval A valid date interval enum value.
   * @param {Number} value The amount to add to the current date.
   * @param {Boolean} [preventDstAdjust=false] `true` to prevent adjustments when crossing
   * daylight savings boundaries.
   * @return {Date} The new Date instance.
   */
  static add(date: Date, interval: string, value: number, preventDstAdjust = false): Date {
    var d = this.clone(date),
      base = 0,
      day,
      decimalValue;

    if (!interval || value === 0) {
      return d;
    }

    decimalValue = value % 1;
    value = Math.trunc(value);

    if (value) {
      switch (interval.toLowerCase()) {
        // We use setTime() here to deal with issues related to
        // the switchover that occurs when changing to daylight savings and vice
        // versa. setTime() handles this correctly where setHour/Minute/Second/Millisecond
        // do not. Let's assume the DST change occurs at 2am and we're incrementing using
        // add for 15 minutes at time. When entering DST, we should see:
        // 01:30am
        // 01:45am
        // 03:00am // skip 2am because the hour does not exist
        // ...
        // Similarly, leaving DST, we should see:
        // 01:30am
        // 01:45am
        // 01:00am // repeat 1am because that's the change over
        // 01:30am
        // 01:45am
        // 02:00am
        // ....
        //
        case this.MILLI:
          if (preventDstAdjust) {
            d.setMilliseconds(d.getMilliseconds() + value);
          } else {
            d.setTime(d.getTime() + value);
          }

          break;

        case this.SECOND:
          if (preventDstAdjust) {
            d.setSeconds(d.getSeconds() + value);
          } else {
            d.setTime(d.getTime() + value * 1000);
          }

          break;

        case this.MINUTE:
          if (preventDstAdjust) {
            d.setMinutes(d.getMinutes() + value);
          } else {
            d.setTime(d.getTime() + value * 60 * 1000);
          }

          break;

        case this.HOUR:
          if (preventDstAdjust) {
            d.setHours(d.getHours() + value);
          } else {
            d.setTime(d.getTime() + value * 60 * 60 * 1000);
          }

          break;

        case this.DAY:
          if (preventDstAdjust === false) {
            d.setTime(d.getTime() + value * 24 * 60 * 60 * 1000);
          } else {
            d.setDate(d.getDate() + value);
          }

          break;

        case this.MONTH:
          day = date.getDate();

          if (day > 28) {
            /* eslint-disable-next-line max-len */
            day = Math.min(
              day,
              this.getLastDateOfMonth(
                this.add(this.getFirstDateOfMonth(date), this.MONTH, value)
              ).getDate()
            );
          }

          d.setDate(day);
          d.setMonth(date.getMonth() + value);

          break;

        case this.YEAR:
          day = date.getDate();

          if (day > 28) {
            /* eslint-disable-next-line max-len */
            day = Math.min(
              day,
              this.getLastDateOfMonth(
                this.add(this.getFirstDateOfMonth(date), this.YEAR, value)
              ).getDate()
            );
          }

          d.setDate(day);
          d.setFullYear(date.getFullYear() + value);

          break;
      }
    }

    if (decimalValue) {
      switch (interval.toLowerCase()) {
        /* eslint-disable no-multi-spaces */
        case this.MILLI:
          base = 1;
          break;
        case this.SECOND:
          base = 1000;
          break;
        case this.MINUTE:
          base = 1000 * 60;
          break;
        case this.HOUR:
          base = 1000 * 60 * 60;
          break;
        case this.DAY:
          base = 1000 * 60 * 60 * 24;
          break;
        /* eslint-enable no-multi-spaces */

        case this.MONTH:
          day = this.getDaysInMonth(d);
          base = 1000 * 60 * 60 * 24 * day;
          break;

        case this.YEAR:
          day = this.isLeapYear(d) ? 366 : 365;
          base = 1000 * 60 * 60 * 24 * day;
          break;
      }

      if (base) {
        d.setTime(d.getTime() + base * decimalValue);
      }
    }

    return d;
  }

  /**
   * Get the numeric day number of the year, adjusted for leap year.
   *
   *     var dt = new Date('9/17/2011');
   *     console.log(DateUtils.getDayOfYear(dt)); // 259
   *
   * @param {Date} date The date
   * @return {Number} 0 to 364 (365 in leap years).
   */
  static getDayOfYear(date: Date): number {
    var num = 0,
      d = this.clone(date),
      m = date.getMonth(),
      i;

    for (i = 0, d.setDate(1), d.setMonth(0); i < m; d.setMonth(++i)) {
      num += this.getDaysInMonth(d);
    }

    return num + date.getDate() - 1;
  }

  /**
   * Get the numeric ISO-8601 week number of the year.
   * (equivalent to the format specifier 'W', but without a leading zero).
   *
   *     var dt = new Date('9/17/2011');
   *     console.log(DateUtils.getWeekOfYear(dt)); // 37
   *
   * @param {Date} date The date.
   * @return {Number} 1 to 53.
   * @method
   */
  static getWeekOfYear = (function () {
    // adapted from http://www.merlyn.demon.co.uk/weekcalc.htm
    var ms1d = 864e5, // milliseconds in a day
      ms7d = 7 * ms1d; // milliseconds in a week

    return (date: Date) => {
      // return a closure so constants get calculated only once
      /* eslint-disable-next-line max-len */
      var DC3 = Date.UTC(date.getFullYear(), date.getMonth(), date.getDate() + 3) / ms1d, // an Absolute Day Number
        AWN = Math.floor(DC3 / 7), // an Absolute Week Number
        Wyr = new Date(AWN * ms7d).getUTCFullYear();

      return AWN - Math.floor(Date.UTC(Wyr, 0, 7) / ms7d) + 1;
    };
  })();

  /**
   * Checks if the current date falls within a leap year.
   *
   *     var dt = new Date('1/10/2011');
   *     console.log(DateUtils.isLeapYear(dt)); // false
   *
   * @param {Date} date The date
   * @return {Boolean} `true` if the current date falls within a leap year, `false` otherwise.
   */
  static isLeapYear(date: Date): boolean {
    var year = date.getFullYear();

    return !!((year & 3) === 0 && (year % 100 || (year % 400 === 0 && year)));
  }

  /**
   * Get the first day of the current month, adjusted for leap year.  The returned value
   * is the numeric day index within the week (0-6) which can be used in conjunction with
   * the {@link #monthNames} array to retrieve the textual day name.
   *
   *     var dt = new Date('1/10/2007'),
   *         firstDay = DateUtils.getFirstDayOfMonth(dt);
   *
   *     console.log(DateUtils.dayNames[firstDay]); // output: 'Monday'
   *
   * @param {Date} date The date
   * @return {Number} The day number (0-6).
   */
  static getFirstDayOfMonth(date: Date): number {
    var day = (date.getDay() - (date.getDate() - 1)) % 7;

    return day < 0 ? day + 7 : day;
  }

  /**
   * Get the last day of the current month, adjusted for leap year.  The returned value
   * is the numeric day index within the week (0-6) which can be used in conjunction with
   * the {@link #monthNames} array to retrieve the textual day name.
   *
   *     var dt = new Date('1/10/2007'),
   *         lastDay = DateUtils.getLastDayOfMonth(dt);
   *
   *     console.log(DateUtils.dayNames[lastDay]); // output: 'Wednesday'
   *
   * @param {Date} date The date
   * @return {Number} The day number (0-6).
   */
  static getLastDayOfMonth(date: Date): number {
    return this.getLastDateOfMonth(date).getDay();
  }

  /**
   * Get the date of the first day of the month in which this date resides.
   * @param {Date} date The date
   * @return {Date}
   */
  static getFirstDateOfMonth(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), 1);
  }

  /**
   * Get the date of the last day of the month in which this date resides.
   * @param {Date} date The date
   * @return {Date}
   */
  static getLastDateOfMonth(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), this.getDaysInMonth(date));
  }

  /**
   * Get the number of days in the current month, adjusted for leap year.
   * @param {Date} date The date
   * @return {Number} The number of days in the month.
   * @method
   */
  static getDaysInMonth = (() => {
    var daysInMonth = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

    return (date: Date) => {
      // return a closure for efficiency
      var m = date.getMonth();

      return m === 1 && DateUtils.isLeapYear(date) ? 29 : daysInMonth[m];
    };
  })();
}
