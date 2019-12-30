package com.easleydp.tempctrl.spring;

import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(ChamberController.class);

    @Autowired
    private ChamberRepository chamberRepository;

    /**
     * This is the initial end point used by the FE home page. The FE may be calling this
     * prospectively, not knowing whether the user is logged-in. Clearly, if the request succeeds
     * the FE has discovered the user is logged-in. But it also needs to know whether the user is
     * admin, so we fold that in too (for convenience).
     */
    @GetMapping("/guest/chamber-summaries-and-user-type")
    public ChamberSummariesAndUserType getChamberSummaries(HttpServletRequest request)
    {
        List<ChamberSummary> chamberSummaries = chamberRepository.getChambers().stream()
                .map(c -> {
                    Gyle ag = c.getActiveGyle();
                    ChamberReadings readings = ag != null ? ag.getLatestReadings() : null;
                    Integer tTarget = readings != null ? readings.gettTarget() : null;
                    return new ChamberSummary(c.getId(), c.getName(), tTarget);
                })
                .collect(Collectors.toList());

        return new ChamberSummariesAndUserType(chamberSummaries, request.isUserInRole("ADMIN"));
    }
    private static final class ChamberSummariesAndUserType
    {
        @SuppressWarnings("unused") public final List<ChamberSummary> chamberSummaries;
        @SuppressWarnings("unused") public final boolean isAdmin;
        public ChamberSummariesAndUserType(List<ChamberSummary> chamberSummaries, boolean isAdmin)
        {
            super();
            this.chamberSummaries = chamberSummaries;
            this.isAdmin = isAdmin;
        }
    }
    private static final class ChamberSummary
    {
        @SuppressWarnings("unused") public final int id;
        @SuppressWarnings("unused") public final String name;
        @SuppressWarnings("unused") public final Integer tTarget;
        public ChamberSummary(int id, String name, Integer tTarget)
        {
            this.id = id;
            this.name = name;
            this.tTarget = tTarget;
        }
    }

    @GetMapping("/guest/chamber/{chamberId}/summary-status")
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
        @SuppressWarnings("unused") public final Integer tTarget;
        @SuppressWarnings("unused") public final Integer tBeer;
        public SummaryStatusResponse(Integer tTarget, Integer tBeer)
        {
            this.tTarget = tTarget;
            this.tBeer = tBeer;
        }
    }

}
