import NowPattern from './NowPattern';
import DateUtils from './DateUtils';

test('isNowPattern false', () => {
  expect(NowPattern.isNowPattern('fooBar')).toEqual(false);
  expect(NowPattern.isNowPattern('now+1x')).toEqual(false);
  expect(NowPattern.isNowPattern('now+1')).toEqual(false);
  expect(NowPattern.isNowPattern('1609246217811')).toEqual(false);
});

test('isNowPattern true', () => {
  expect(NowPattern.isNowPattern('now')).toEqual(true);
  expect(NowPattern.isNowPattern(' now+5m ')).toEqual(true);
  expect(NowPattern.isNowPattern('now +1h')).toEqual(true);
  expect(NowPattern.isNowPattern('now-1h')).toEqual(true);
  expect(NowPattern.isNowPattern('now+7d')).toEqual(true);
});

test('evaluateNowPattern bad', () => {
  expect(() => {
    NowPattern.evaluateNowPattern('fooBar');
  }).toThrow();
});

test('evaluateNowPattern good', () => {
  const now = new Date();
  NowPattern.setNowForTesting(now);

  expect(NowPattern.evaluateNowPattern('now')).toEqual(now);
  expect(NowPattern.evaluateNowPattern('now+5m')).toEqual(DateUtils.add(now, DateUtils.MINUTE, 5));
  expect(NowPattern.evaluateNowPattern('now+1h')).toEqual(DateUtils.add(now, DateUtils.HOUR, 1));
  expect(NowPattern.evaluateNowPattern('now - 1h')).toEqual(DateUtils.add(now, DateUtils.HOUR, -1));
  expect(NowPattern.evaluateNowPattern('now+7d')).toEqual(DateUtils.add(now, DateUtils.DAY, 7));
});
