package com.easleydp.tempctrl.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.easleydp.tempctrl.domain.Utils.roundToNearestHour;

import java.util.Calendar;
import java.util.Date;

import org.junit.jupiter.api.Test;

public class UtilsTests {
    @Test
    public void testRoundDateToNearestHour1() {
        Date d = buildDate(2022, 8, 22, 15, 29);
        assertEquals(roundToNearestHour(d), buildDate(2022, 8, 22, 15, 0));
    }

    @Test
    public void testRoundDateToNearestHour2() {
        Date d = buildDate(2022, 8, 22, 15, 31);
        assertEquals(roundToNearestHour(d), buildDate(2022, 8, 22, 16, 0));
    }

    private static Date buildDate(int year, int month /* zero based! */, int date, int hourOfDay, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(year, month - 1, date, hourOfDay, minute, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }
}
