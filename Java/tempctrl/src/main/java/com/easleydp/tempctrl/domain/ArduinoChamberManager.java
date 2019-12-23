package com.easleydp.tempctrl.domain;

import java.util.Date;

import org.springframework.core.env.Environment;

public class ArduinoChamberManager implements ChamberManager
{

    public ArduinoChamberManager(ChamberRepository chamberRepository, Environment env)
    {
        // TODO Auto-generated constructor stub
    }

    @Override
    public void setParameters(int chamberId, ChamberParameters params)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public ChamberReadings getReadings(int chamberId, Date timeNow)
    {
        // TODO Auto-generated method stub
        return null;
    }

}
