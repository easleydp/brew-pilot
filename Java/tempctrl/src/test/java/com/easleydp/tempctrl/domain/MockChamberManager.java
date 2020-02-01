package com.easleydp.tempctrl.domain;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

public class MockChamberManager implements ChamberManager
{
    private Map<Integer, ChamberParameters> chamberParametersById = new HashMap<>();

    private Random random;

    /**
     * When the temperatureProfile should be considered to have started.
     * @param startTime  Some point in time >= startTime
     */
    public MockChamberManager(Date startTime, TemperatureProfile temperatureProfile, Environment env)
    {
        this.startTime = startTime;
        this.temperatureProfile = temperatureProfile;
    }

    @Override
    public void setParameters(int chamberId, ChamberParameters chamberParameters)
    {
        chamberParametersById.put(chamberId,  chamberParameters);
    }

    @Override
    public ChamberReadings getReadings(int chamberId, Date timeNow)
    {
        random = new Random(timeNow.hashCode() + chamberId * 3);

        Assert.state(nowTime != null, "nowTime should be set before calling this method.");
        Assert.state(startTime != null, "startTime should be set before calling this method.");
        long millisSinceStart = nowTime.getTime() - startTime.getTime();
        Assert.state(millisSinceStart >= 0, "nowTime should not be before startTime.");
        Assert.state(temperatureProfile != null, "temperatureProfile should be set before calling this method.");

        int tTarget, tTargetNext, tMin, tMax;
        ChamberParameters params = chamberParametersById.get(chamberId);
        if (params == null) {
            tTarget = temperatureProfile.getTargetTempAt(millisSinceStart);
            tTargetNext = temperatureProfile.getTargetTempAt(millisSinceStart + 1000L * 60 * 60);
            tMin = -5 * 10;
            tMax = 40 * 10;
            params = new ChamberParameters(tTarget, tTargetNext, tMin, tMax);
        }
        else
        {
            tTarget = params.tTarget;
        }

        int tBeer = tTarget + randomInt(-20, 20);
        int tExternal = lastTExternal = getExternalTempFromDate(timeNow, lastTExternal);
        int tChamber = ((tBeer + tExternal) / 2) + randomInt(-20, 20);
        int tPi = tExternal + randomInt(50, 70);
        int heaterOutput = getDayOfMonthFromDate(nowTime) % 2 == 0 ? randomInt(1, 100) : 0;
        boolean coolerOn = heaterOutput == 0;
        Mode mode = Mode.AUTO;
        return new ChamberReadings(timeNow,
                tTarget, tBeer, tExternal, tChamber, tPi, heaterOutput, coolerOn, mode, params);
    }
    private Integer lastTExternal = null;

    /** @returns degrees C x 10 */
    private int getExternalTempFromDate(Date date, Integer prevTemp)
    {
        int tendTowards = _getExternalTempFromDate(date);
        if (prevTemp == null)
            return tendTowards;
        int diff = tendTowards - prevTemp;
        if (diff > 0)
            return prevTemp + randomInt(1, 2);
        else
            return prevTemp - randomInt(1, 2);
    }
    private int _getExternalTempFromDate(Date date)
    {
        int nHours = date.getHours();
        if (0 <= nHours && nHours < 3)
            return 30;
        if (3 <= nHours && nHours < 6)
            return 10;
        if (6 <= nHours && nHours < 9)
            return 20;
        if (9 <= nHours && nHours < 12)
            return 100;
        if (12 <= nHours && nHours < 15)
            return 250;
        if (15 <= nHours && nHours < 18)
            return 200;
        if (18 <= nHours && nHours < 21)
            return 120;
        return 50;
    }

    private static int getDayOfMonthFromDate(Date d)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(d);
        return cal.get(Calendar.DAY_OF_MONTH);
    }

    private int randomInt(int from, int to)
    {
        return from + random.nextInt(to - from + 1);
    }


    /*
     * The following fields and methods are only applicable to the simulator and would make no
     * sense for a non-test ChamberManager.
     */

    private final TemperatureProfile temperatureProfile;
    /** startTime provides the reference for when temperatureProfile was started. */
    private final Date startTime;
    /** nowTime - startTime is used when interpolating temperatureProfile. */
    private Date nowTime = null;

    /**
     * Whereas a genuine ChamberManager impl simply returns current values, this simulator doesn't operate
     * in real time. Rather, it must be told where it is in the preprogrammed story. This principle allows
     * tests to run faster than real time.
     * @param now  Some point in time >= startTime
     */
    public void setNowTime(Date now)
    {
        this.nowTime = now;
    }

}
