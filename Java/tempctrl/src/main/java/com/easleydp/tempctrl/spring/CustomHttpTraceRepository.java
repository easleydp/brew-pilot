package com.easleydp.tempctrl.spring;

import org.springframework.boot.actuate.trace.http.InMemoryHttpTraceRepository;
import org.springframework.stereotype.Repository;

@Repository
public class CustomHttpTraceRepository extends InMemoryHttpTraceRepository
{
    // No customisation for now
}
