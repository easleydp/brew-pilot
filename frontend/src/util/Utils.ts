export default class Utils {
  static getErrorMessage(error: any): string {
    const fullMessage = error?.response?.data?.message ?? '' + error;
    // If this marker appears anywhere in the string then we assume the friendly message comes after.
    const MARKER_TXT = 'Exception:';
    const i = fullMessage.indexOf(MARKER_TXT);
    return i !== -1 ? fullMessage.substr(i + MARKER_TXT.length).trimLeft() : fullMessage;
  }
}
