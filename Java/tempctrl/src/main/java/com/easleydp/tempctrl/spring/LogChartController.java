package com.easleydp.tempctrl.spring;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@RestController
public class LogChartController
{
    @GetMapping("/log-chart/status")
    public StatusReportResponse getStatusReport()
    {
        return new StatusReportResponse(101, null);
    }

    private static final class StatusReportResponse
    {
        @SuppressWarnings("unused")
        public final int code;

        @JsonInclude(Include.NON_NULL)
        public final String desc;

        public StatusReportResponse(int code, String desc)
        {
            this.code = code;
            this.desc = desc;
        }
    }

}
