package com.easleydp.tempctrl.domain;

import java.util.Calendar;
import java.util.Date;
import java.util.Random;

import org.springframework.util.Assert;

import com.easleydp.tempctrl.domain.ChamberReadings.Mode;

public class MockChamberManager implements ChamberManager
{
    private ChamberParameters chamberParameters;

    private Random random;

    @Override
    public void setParameters(int chamberId, ChamberParameters chamberParameters)
    {
        this.chamberParameters = chamberParameters;
    }

    @Override
    public ChamberReadings getReadings(int chamberId, Date timeNow)
    {
        random = new Random(chamberId);

        Assert.state(nowTime != null, "nowTime should be set before calling this method.");
        Assert.state(startTime != null, "startTime should be set before calling this method.");
        int millisSinceStart = (int) (nowTime.getTime() - startTime.getTime());
        Assert.state(millisSinceStart >= 0, "nowTime should not be before startTime.");
        Assert.state(temperatureProfile != null, "temperatureProfile should be set before calling this method.");

        int tTarget, tTargetNext, tMin, tMax;
        ChamberParameters params = this.chamberParameters;
        if (params == null) {
            tTarget = temperatureProfile.getTargetTempAt(millisSinceStart);
            tTargetNext = temperatureProfile.getTargetTempAt(millisSinceStart + 1000 * 60 * 60);
            tMin = -5 * 10;
            tMax = 40 * 10;
            params = new ChamberParameters(tTarget, tTargetNext, tMin, tMax);
        }
        else
        {
            tTarget = params.tTarget;
            tTargetNext = params.tTargetNext;
            tMin = params.tMin;
            tMax = params.tMax;
        }

        int tBeer = tTarget + randomInt(-2, 2);
        int tExternal = tBeer + randomInt(-4, 4);
        int tChamber = tExternal + randomInt(4, 6);
        int tPi = tExternal + randomInt(5, 7);
        int heaterOutput = getDayOfMonthFromDate(nowTime) % 2 == 0 ? randomInt(1, 100) : 0;
        boolean coolerOn = heaterOutput == 0;
        Mode mode = Mode.AUTO;
        return new ChamberReadings(timeNow,
                tTarget, tBeer, tExternal, tChamber, tPi, heaterOutput, coolerOn, mode, params);
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
     * When the temperatureProfile should be considered to have started.
     * @param startTime  Some point in time >= startTime
     */
    public MockChamberManager(Date startTime, TemperatureProfile temperatureProfile)
    {
        this.startTime = startTime;
        this.temperatureProfile = temperatureProfile;
    }

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
