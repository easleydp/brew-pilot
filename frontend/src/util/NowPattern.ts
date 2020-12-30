import DateUtils from './DateUtils';

export default class NowPattern {
  /**
   * @returns if the supplied string value looks like a 'now' pattern.
   * Also initialises the RegExp '$' properties.
   */
  static isNowPattern(value: string): boolean {
    // Note: `\d+` rather than `\d{1,7}` in this regex would leave us vulnerable to locking
    // up the browser if the user ever entered a ridiculously large value such as 99999999.
    if (!value.trim) {
      debugger;
    }
    return value.trim().match(/^now\s*((-|\+)\s*(\d{1,7})\s*([mhd]))?$/i) !== null;
  }

  /**
   * Evaluates a valid 'now' pattern.
   * @returns {Date}
   */
  static evaluateNowPattern(np: string): Date {
    if (!this.isNowPattern(np))
      // This also ensures the RegExp '$' properties are initialised.
      throw Error("Program error - invalid 'now pattern': " + np);

    const now = this.nowForTesting || new Date();
    if (!RegExp.$2) {
      return now;
    }

    var sign = RegExp.$2,
      count = parseInt(RegExp.$3, 10),
      type = RegExp.$4.toLowerCase(),
      unitType = function () {
        switch (type) {
          case 'm':
            return DateUtils.MINUTE;
          case 'h':
            return DateUtils.HOUR;
          case 'd':
            return DateUtils.DAY;
          default:
            throw Error('Unanticipated type: ' + type);
        }
      };

    if (sign === '-') count = count * -1;

    return DateUtils.add(now, unitType(), count);
  }

  // For testing only
  private static nowForTesting: Date | null = null;
  static setNowForTesting(nowForTesting: Date) {
    this.nowForTesting = nowForTesting;
  }
}
