import React from 'react';
import DateUtils from './DateUtils';

test('add', () => {
  const d = new Date('2018-12-30');
  expect(DateUtils.add(d, DateUtils.DAY, -1)).toEqual(new Date('2018-12-29'));
  expect(DateUtils.add(d, DateUtils.DAY, 1)).toEqual(new Date('2018-12-31'));
  expect(DateUtils.add(d, DateUtils.DAY, 2)).toEqual(new Date('2019-01-01'));

  expect(DateUtils.add(d, DateUtils.MONTH, 1)).toEqual(new Date('2019-01-30'));
  expect(DateUtils.add(d, DateUtils.YEAR, 1)).toEqual(new Date('2019-12-30'));
});
