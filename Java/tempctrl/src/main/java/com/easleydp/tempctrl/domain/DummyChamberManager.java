package com.easleydp.tempctrl.domain;

import java.util.Calendar;
import java.util.Date;
import java.util.Random;

/**
 * A ChamberManager that can be used for convenience to remove the need for actual hardware.
 */
public class DummyChamberManager implements ChamberManager
{
    private ChamberRepository chamberRepository;
    private ChamberParameters chamberParameters;

    /** startTime provides the reference for when temperatureProfile was started. */
    private final Date startTime;

    private Random random;


    public DummyChamberManager(ChamberRepository chamberRepository)
    {
        this.chamberRepository = chamberRepository;
        this.startTime = new Date();
    }

    @Override
    public void setParameters(int chamberId, ChamberParameters params)
    {
        this.chamberParameters = params;
    }

    @Override
    public ChamberReadings getReadings(int chamberId, Date timeNow)
    {
        Gyle gyle = chamberRepository.getChamberById(chamberId).getLatestGyle();
        if (gyle == null  ||  !gyle.isActive())
            throw new IllegalStateException("Change " + chamberId + " has no active gyle.");

        TemperatureProfile temperatureProfile = gyle.getTemperatureProfile();

        random = new Random(timeNow.hashCode() + chamberId * 3);

        Date nowTime = new Date();
        long millisSinceStart = nowTime.getTime() - startTime.getTime();

        int tTarget, tTargetNext, tMin, tMax;
        ChamberParameters params = this.chamberParameters;
        if (params == null) {
            tTarget = temperatureProfile.getTargetTempAt(millisSinceStart);
            tTargetNext = temperatureProfile.getTargetTempAt(millisSinceStart + 1000L * 60 * 60);
            tMin = -1 * 10;
            tMax = 41 * 10;
            params = new ChamberParameters(tTarget, tTargetNext, tMin, tMax, true, 10, 10, 0, 1.2, 2.3, 3.4, Mode.AUTO);
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
        boolean fridgeOn = heaterOutput == 0;
        Mode mode = Mode.AUTO;
        return new ChamberReadings(timeNow,
                tTarget, tBeer, tExternal, tChamber, tPi, heaterOutput, fridgeOn, mode, params);
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

    @Override
    public void slurpLogMessages()
    {
        // TODO Auto-generated method stub

    }
}
