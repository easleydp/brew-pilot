package com.easleydp.tempctrl.spring;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Comparator;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.easleydp.tempctrl.domain.Chamber;
import com.easleydp.tempctrl.domain.ChamberReadings;
import com.easleydp.tempctrl.domain.ChamberRepository;
import com.easleydp.tempctrl.domain.Gyle;
import com.easleydp.tempctrl.domain.GyleDto;
import com.easleydp.tempctrl.domain.PointDto;
import com.easleydp.tempctrl.domain.PropertyUtils;
import com.easleydp.tempctrl.domain.TemperatureProfileDto;

@RestController
public class ChamberController {
    private static final Logger logger = LoggerFactory.getLogger(ChamberController.class);

    @Autowired
    private ChamberRepository chamberRepository;

    /**
     * This is the initial end point used by the FE home page. The FE may be calling
     * this prospectively, not knowing whether the user is logged-in. Clearly, if
     * the request succeeds the FE has discovered the user is logged-in. But it also
     * needs to know whether the user is admin, so we fold that in too (for
     * convenience).
     */
    @GetMapping("/guest/chamber-summaries-and-user-type")
    public ChamberSummariesAndUserType getChamberSummaries(HttpServletRequest request) {
        // @formatter:off
        List<ChamberSummary> chamberSummaries = chamberRepository.getChambers().stream()
            .map(c -> {

                // FE infers chamber is inactive if tTarget is null
                Gyle lg = c.getLatestGyle();
                boolean active = lg != null && lg.isActive();
                ChamberReadings readings = active ? c.getLatestChamberReadings() : null;
                Integer tTarget = readings != null ? readings.gettTarget() : null;

                return new ChamberSummary(c.getId(), c.getName(), tTarget);
            })
            .collect(Collectors.toList());
        // @formatter:on

        return new ChamberSummariesAndUserType(chamberSummaries, request.isUserInRole("ADMIN"));
    }

    private static final class ChamberSummariesAndUserType {
        @SuppressWarnings("unused")
        public final List<ChamberSummary> chamberSummaries;
        @SuppressWarnings("unused")
        public final boolean isAdmin;

        public ChamberSummariesAndUserType(List<ChamberSummary> chamberSummaries, boolean isAdmin) {
            this.chamberSummaries = chamberSummaries;
            this.isAdmin = isAdmin;
        }
    }

    private static final class ChamberSummary {
        @SuppressWarnings("unused")
        public final int id;
        @SuppressWarnings("unused")
        public final String name;
        // Including tTarget is legit since it also serves to signify whether the
        // chamber is active.
        @SuppressWarnings("unused")
        public final Integer tTarget;

        public ChamberSummary(int id, String name, Integer tTarget) {
            this.id = id;
            this.name = name;
            this.tTarget = tTarget;
        }
    }

    /**
     * Polled by each home page gauge
     */
    @GetMapping("/guest/chamber/{chamberId}/beer-temp")
    public BeerTemp getSummaryStatus(@PathVariable("chamberId") int chamberId) {
        Chamber chamber = chamberRepository.getChamberById(chamberId); // throws if not found
        ChamberReadings latestReadings = chamber.getLatestChamberReadings();

        // FE infers chamber is inactive if tTarget is null
        Gyle lg = chamber.getLatestGyle();
        boolean active = lg != null && lg.isActive();
        Integer tTarget = active ? latestReadings.gettTarget() : null;

        return new BeerTemp(tTarget, latestReadings.gettBeer());
    }

    private static final class BeerTemp {
        @SuppressWarnings("unused")
        public final Integer tTarget; // null signifies temp control is inactive
        @SuppressWarnings("unused")
        public final Integer tBeer;

        public BeerTemp(Integer tTarget, Integer tBeer) {
            this.tTarget = tTarget;
            this.tBeer = tBeer;
        }
    }

    /**
     * Called by 'Gyle Chart' view to retrieve data for the specified chamber's
     * latest gyle.
     */
    @GetMapping("/guest/chamber/{chamberId}/latest-gyle-details")
    public LatestGyleDetails getLatestGyleDetails(@PathVariable("chamberId") int chamberId) {
        Chamber chamber = chamberRepository.getChamberById(chamberId); // throws if not found
        Gyle latestGyle = chamber.getLatestGyle();
        Assert.state(latestGyle != null, "No latest gyle for chamber " + chamberId);
        // @formatter:off
        return new LatestGyleDetails(PropertyUtils.getReadingsTimestampResolutionMillis(),
                PropertyUtils.getReadingsPeriodMillis(), chamber.getName(), chamber.isHasHeater(), latestGyle.id,
                latestGyle.getName(), latestGyle.getTemperatureProfile(), latestGyle.getDtStarted(),
                latestGyle.getDtEnded(), latestGyle.getRecentReadingsList(),
                latestGyle.getReadingsLogFilePaths().stream()
                        .map(path -> path.getFileName().toString().replace(".ndjson", ""))
                        .collect(Collectors.toList()));
        // @formatter:on
    }

    private static final class LatestGyleDetails {
        @SuppressWarnings("unused")
        public final int readingsTimestampResolutionMillis;
        @SuppressWarnings("unused")
        public final int readingsPeriodMillis;
        @SuppressWarnings("unused")
        public final String chamberName;
        @SuppressWarnings("unused")
        public final boolean hasHeater;
        @SuppressWarnings("unused")
        public final int gyleId;
        @SuppressWarnings("unused")
        public final String gyleName;
        @SuppressWarnings("unused")
        public final TemperatureProfileDto temperatureProfile;
        @SuppressWarnings("unused")
        public final Long dtStarted;
        @SuppressWarnings("unused")
        public final Long dtEnded;
        @SuppressWarnings("unused")
        public final List<ChamberReadings> recentReadings;
        @SuppressWarnings("unused")
        public final List<String> readingsLogs;

        public LatestGyleDetails(int readingsTimestampResolutionMillis, int readingsPeriodMillis, String chamberName,
                boolean hasHeater, int gyleId, String gyleName, TemperatureProfileDto temperatureProfile,
                Long dtStarted, Long dtEnded, List<ChamberReadings> recentReadings, List<String> readingsLogs) {
            this.readingsTimestampResolutionMillis = readingsTimestampResolutionMillis;
            this.readingsPeriodMillis = readingsPeriodMillis;
            this.chamberName = chamberName;
            this.hasHeater = hasHeater;
            this.gyleId = gyleId;
            this.gyleName = gyleName;
            this.temperatureProfile = temperatureProfile;
            this.dtStarted = dtStarted;
            this.dtEnded = dtEnded;
            this.recentReadings = recentReadings;
            this.readingsLogs = readingsLogs;
        }

    }

    // Helper
    private Gyle getLatestGyleForChamber(int chamberId) {
        Chamber chamber = chamberRepository.getChamberById(chamberId); // throws if not found
        Gyle latestGyle = chamber.getLatestGyle();
        Assert.state(latestGyle != null, "No latest gyle for chamber " + chamberId);
        return latestGyle;
    }

    /**
     * Polled by 'Gyle Chart' view to get all readings for the specified chamber's
     * latest gyle since the specified time.
     */
    @GetMapping("/guest/chamber/{chamberId}/recent-readings")
    public List<ChamberReadings> getLatestGyleRecentReadings(@PathVariable("chamberId") int chamberId,
            @RequestParam(value = "sinceDt", required = true) int sinceDt) {
        Gyle latestGyle = getLatestGyleForChamber(chamberId);
        // @formatter:off
        return latestGyle.getRecentReadingsList().stream()
            .filter(cr -> cr.getDt() > sinceDt)
            .collect(Collectors.toList());
        // @formatter:on
    }

    /**
     * Called by 'Temperature Profile' view to retrieve data for the specified
     * chamber's latest gyle.
     */
    @GetMapping("/guest/chamber/{chamberId}/latest-gyle-profile")
    public TemperatureProfileDto getLatestGyleProfile(@PathVariable("chamberId") int chamberId) {
        return getLatestGyleForChamber(chamberId).getTemperatureProfile();
    }

    @PostMapping("/admin/chamber/{chamberId}/latest-gyle-profile")
    public void updateLatestGyleProfile(@PathVariable("chamberId") int chamberId,
            @RequestBody TemperatureProfileDto profile) {
        logger.info("POST latest-gyle-profile, {},\n\t{}", chamberId, profile);
        Gyle latestGyle = getLatestGyleForChamber(chamberId);
        TemperatureProfileDto currentProfile = latestGyle.getTemperatureProfile();
        if (currentProfile.equals(profile)) { // optimisation
            logger.info(" ... no change.");
            return;
        }
        logger.info(" ... was:\n\t{}", currentProfile);
        latestGyle.setTemperatureProfile(profile);

        try {
            latestGyle.updateJsonFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/guest/chamber/{chamberId}/latest-gyle")
    public GyleDto getLatestGyle(@PathVariable("chamberId") int chamberId) {
        return getLatestGyleForChamber(chamberId);
    }

    @PostMapping("/admin/chamber/{chamberId}/latest-gyle")
    public void updateLatestGyle(@PathVariable("chamberId") int chamberId, @RequestBody GyleDto gyle) {
        logger.info("POST latest-gyle, {}, {}", chamberId, gyle);
        Gyle latestGyle = getLatestGyleForChamber(chamberId);
        BeanUtils.copyProperties(gyle, latestGyle);

        try {
            latestGyle.updateJsonFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/guest/chamber/{chamberId}/recent-gyles")
    public List<GyleNameIdDuration> getRecentGyles(@PathVariable("chamberId") int chamberId,
            @RequestParam("max") Integer max) {
        Chamber chamber = chamberRepository.getChamberById(chamberId); // throws if not found
        return chamber.getGyles(max).stream()
                .map(g -> new GyleNameIdDuration(g))
                .collect(Collectors.toList());
    }

    private static final class GyleNameIdDuration {
        @SuppressWarnings("unused")
        public final String name;
        @SuppressWarnings("unused")
        public final int id;
        @SuppressWarnings("unused")
        public final int durationHrs;
        @SuppressWarnings("unused")
        public final int startTemp;
        @SuppressWarnings("unused")
        public final int maxTemp;

        public GyleNameIdDuration(Gyle gyle) {
            this.name = gyle.getName();
            this.id = gyle.id;

            List<PointDto> points = gyle.getTemperatureProfile().getPoints();
            this.durationHrs = points.get(points.size() - 1).getHoursSinceStart();
            this.startTemp = points.get(0).getTargetTemp();
            this.maxTemp = Collections.max(points, Comparator.comparing(PointDto::getTargetTemp)).getTargetTemp();
        }
    }

    @PostMapping("/admin/chamber/{chamberId}/create-gyle")
    public void createGyle(@PathVariable("chamberId") int chamberId, @RequestBody CreateGyleDto createGyle) {
        logger.info("POST create-gyle, {}, {}, {}", chamberId, createGyle.gyleToCopyId, createGyle.newName);
        Chamber chamber = chamberRepository.getChamberById(chamberId);
        Gyle gyleToCopy = chamber.getGyleById(createGyle.gyleToCopyId);

        try {
            chamber.constructNextGyle(gyleToCopy, "#%d - " + createGyle.newName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class CreateGyleDto {
        public int gyleToCopyId;
        public String newName;
    }

    @PreDestroy
    public void destroy() {
        logger.info("**** destroy ****");
        // @formatter:off
        chamberRepository.getChambers().stream()
            .forEach(chamber -> {
                Gyle latestGyle = chamber.getLatestGyle();
                if (latestGyle != null && latestGyle.isActive()) {
                    logger.info("Closing chamber {}, gyle {}", chamber.getId(), latestGyle.id);
                    latestGyle.close();
                }
            });
        // @formatter:on
    }

}
