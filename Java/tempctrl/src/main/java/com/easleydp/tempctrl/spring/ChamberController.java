package com.easleydp.tempctrl.spring;

import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.easleydp.tempctrl.domain.Chamber;
import com.easleydp.tempctrl.domain.ChamberReadings;
import com.easleydp.tempctrl.domain.ChamberRepository;
import com.easleydp.tempctrl.domain.Gyle;
import com.easleydp.tempctrl.domain.PropertyUtils;
import com.easleydp.tempctrl.domain.TemperatureProfileDto;

@RestController
public class ChamberController
{
    // private static final Logger logger = LoggerFactory.getLogger(ChamberController.class);

    @Autowired
    private ChamberRepository chamberRepository;

// TODO: Delete is still commented-out after April 2020
//    /**
//     * FE calls this to discover whether the user is logged-in. Clearly, if the request succeeds
//     * the FE has discovered the user is logged-in. Returns true if the user is admin.
//     */
//    @GetMapping("/guest/user-type")
//    public UserType getUserType(HttpServletRequest request)
//    {
//        return new UserType(request.isUserInRole("ADMIN"));
//    }
//    private static final class UserType
//    {
//        @SuppressWarnings("unused") public final boolean isAdmin;
//        public UserType(boolean isAdmin)
//        {
//            this.isAdmin = isAdmin;
//        }
//    }
//
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

    /**
     * Polled by each home page gauge
     */
    @GetMapping("/guest/chamber/{chamberId}/summary-status")
    public SummaryStatus getSummaryStatus(@PathVariable("chamberId") int chamberId)
    {
        Chamber chamber = chamberRepository.getChamberById(chamberId); // throws if not found
        Gyle activeGyle = chamber.getActiveGyle();
        ChamberReadings latestReadings = activeGyle != null ? activeGyle.getLatestReadings() : null;
        if (latestReadings == null)
            return new SummaryStatus(null, null);
        return new SummaryStatus(latestReadings.gettTarget(), latestReadings.gettBeer());
    }
    private static final class SummaryStatus
    {
        @SuppressWarnings("unused") public final Integer tTarget;
        @SuppressWarnings("unused") public final Integer tBeer;
        public SummaryStatus(Integer tTarget, Integer tBeer)
        {
            this.tTarget = tTarget;
            this.tBeer = tBeer;
        }
    }

    /**
     * Called by 'Gyle Chart' view to retrieve data for the specified chamber's active gyle.
     */
    @GetMapping("/guest/chamber/{chamberId}/active-gyle-details")
    public ActiveGyleDetails getActiveGyleDetails(@PathVariable("chamberId") int chamberId)
    {
        Chamber chamber = chamberRepository.getChamberById(chamberId); // throws if not found
        Gyle activeGyle = chamber.getActiveGyle();
        Assert.state(activeGyle != null, "No active gyle for chamber " + chamberId);
        return new ActiveGyleDetails(
                PropertyUtils.getReadingsTimestampResolutionMillis(),
                PropertyUtils.getReadingsPeriodMillis(),
                chamber.getName(),
                chamber.isHasHeater(),
                activeGyle.getId(), activeGyle.getName(), activeGyle.getTemperatureProfile(), activeGyle.getDtStarted(),
                activeGyle.getRecentReadings(),
                activeGyle.getReadingsLogFilePaths().stream()
                        .map(path -> path.getFileName().toString().replace(".ndjson", ""))
                        .collect(Collectors.toList()));
    }
    private static final class ActiveGyleDetails
    {
        @SuppressWarnings("unused") public final int readingsTimestampResolutionMillis;
        @SuppressWarnings("unused") public final int readingsPeriodMillis;
        @SuppressWarnings("unused") public final String chamberName;
        @SuppressWarnings("unused") public final boolean hasHeater;
        @SuppressWarnings("unused") public final int gyleId;
        @SuppressWarnings("unused") public final String gyleName;
        @SuppressWarnings("unused") public final TemperatureProfileDto temperatureProfile;
        @SuppressWarnings("unused") public final long dtStarted;
        @SuppressWarnings("unused") public final List<ChamberReadings> recentReadings;
        @SuppressWarnings("unused") public final List<String> readingsLogs;
        public ActiveGyleDetails(int readingsTimestampResolutionMillis, int readingsPeriodMillis,
                String chamberName, boolean hasHeater, int gyleId, String gyleName,
                TemperatureProfileDto temperatureProfile, long dtStarted, List<ChamberReadings> recentReadings,
                List<String> readingsLogs)
        {
            this.readingsTimestampResolutionMillis = readingsTimestampResolutionMillis;
            this.readingsPeriodMillis = readingsPeriodMillis;
            this.chamberName = chamberName;
            this.hasHeater = hasHeater;
            this.gyleId = gyleId;
            this.gyleName = gyleName;
            this.temperatureProfile = temperatureProfile;
            this.dtStarted = dtStarted;
            this.recentReadings = recentReadings;
            this.readingsLogs = readingsLogs;
        }

    }

    /**
     * Polled by 'Gyle Chart' view to get all readings for the specified chamber's active gyle
     * since the specified time.
     */
    @GetMapping("/guest/chamber/{chamberId}/recent-readings")
    public List<ChamberReadings> getActiveGyleRecentReadings(
            @PathVariable("chamberId") int chamberId,
            @RequestParam(value="sinceDt", required=true) int sinceDt)
    {
        Chamber chamber = chamberRepository.getChamberById(chamberId); // throws if not found
        Gyle activeGyle = chamber.getActiveGyle();
        Assert.state(activeGyle != null, "No active gyle for chamber " + chamberId);
        return activeGyle.getRecentReadings().stream()
                .filter(cr -> cr.getDt() > sinceDt)
                .collect(Collectors.toList());
    }

    /**
     * Called by 'Fermentation Profile' view to retrieve data for the specified chamber's active gyle.
     */
    @GetMapping("/guest/chamber/{chamberId}/active-gyle-profile")
    public TemperatureProfileDto getActiveGyleProfile(@PathVariable("chamberId") int chamberId)
    {
        Chamber chamber = chamberRepository.getChamberById(chamberId); // throws if not found
        Gyle activeGyle = chamber.getActiveGyle();
        Assert.state(activeGyle != null, "No active gyle for chamber " + chamberId);
        return activeGyle.getTemperatureProfile();
    }

}
