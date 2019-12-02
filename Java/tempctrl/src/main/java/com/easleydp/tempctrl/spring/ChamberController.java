package com.easleydp.tempctrl.spring;

import java.nio.file.Path;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChamberController
{
    @Autowired
    private Path dataDir;

    @PostConstruct
    public void init()
    {
        // dataDir...
    }
}
