package com.easleydp.tempctrl.spring;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.easleydp.tempctrl.domain.Chamber;
import com.easleydp.tempctrl.domain.ChamberReadings;
import com.easleydp.tempctrl.domain.ChamberRepository;
import com.easleydp.tempctrl.domain.Gyle;

@RestController
public class ChamberController
{
    @Autowired
    private ChamberRepository chamberRepository;

    @GetMapping("/chamber-summaries")
    public List<ChamberSummary> getChamberSummaries()
    {
        return chamberRepository.getChambers().stream()
                .map(c -> {
                    Gyle ag = c.getActiveGyle();
                    ChamberReadings readings = ag != null ? ag.getLatestReadings() : null;
                    Integer tTarget = readings != null ? readings.gettTarget() : null;
                    return new ChamberSummary(c.getId(), c.getName(), tTarget);
                })
                .collect(Collectors.toList());
    }
    private static final class ChamberSummary
    {
        @SuppressWarnings("unused")
        public final int id;

        @SuppressWarnings("unused")
        public final String name;

        @SuppressWarnings("unused")
        public final Integer tTarget;

        public ChamberSummary(int id, String name, Integer tTarget)
        {
            this.id = id;
            this.name = name;
            this.tTarget = tTarget;
        }
    }

    @GetMapping("/chamber/{chamberId}/summary-status")
    public SummaryStatusResponse getSummaryStatus(@PathVariable("chamberId") int chamberId)
    {
        Chamber chamber = chamberRepository.getChamberById(chamberId);
        Gyle activeGyle = chamber.getActiveGyle();
        ChamberReadings latestReadings = activeGyle != null ? activeGyle.getLatestReadings() : null;
        if (latestReadings == null)
            return new SummaryStatusResponse(null, null);
        return new SummaryStatusResponse(latestReadings.gettTarget(), latestReadings.gettBeer());
    }
    private static final class SummaryStatusResponse
    {
        @SuppressWarnings("unused")
        public final Integer tTarget;

        @SuppressWarnings("unused")
        public final Integer tBeer;

        public SummaryStatusResponse(Integer tTarget, Integer tBeer)
        {
            this.tTarget = tTarget;
            this.tBeer = tBeer;
        }
    }

}
