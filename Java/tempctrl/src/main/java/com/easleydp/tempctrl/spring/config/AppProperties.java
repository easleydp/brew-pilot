package com.easleydp.tempctrl.spring.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String base;
    private String staticContent;
    private String dataDir;
    private boolean dummyChambers;
    private boolean debug;
    private TempProfile tempProfile = new TempProfile();
    private PasswordHashes passwordHashes = new PasswordHashes();
    private Mail mail = new Mail();
    private Readings readings = new Readings();
    private OfflineCheck offlineCheck = new OfflineCheck();
    private ColdCrashCheck coldCrashCheck = new ColdCrashCheck();
    private SwitchedOffCheck switchedOffCheck = new SwitchedOffCheck();
    private StillAliveMessage stillAliveMessage = new StillAliveMessage();
    private Timezone timezone = new Timezone();
    private PublicIpChange publicIpChange;

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public String getStaticContent() {
        return staticContent;
    }

    public void setStaticContent(String staticContent) {
        this.staticContent = staticContent;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public boolean isDummyChambers() {
        return dummyChambers;
    }

    public void setDummyChambers(boolean dummyChambers) {
        this.dummyChambers = dummyChambers;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public TempProfile getTempProfile() {
        return tempProfile;
    }

    public void setTempProfile(TempProfile tempProfile) {
        this.tempProfile = tempProfile;
    }

    public PasswordHashes getPasswordHashes() {
        return passwordHashes;
    }

    public void setPasswordHashes(PasswordHashes passwordHashes) {
        this.passwordHashes = passwordHashes;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public Readings getReadings() {
        return readings;
    }

    public void setReadings(Readings readings) {
        this.readings = readings;
    }

    public OfflineCheck getOfflineCheck() {
        return offlineCheck;
    }

    public void setOfflineCheck(OfflineCheck offlineCheck) {
        this.offlineCheck = offlineCheck;
    }

    public ColdCrashCheck getColdCrashCheck() {
        return coldCrashCheck;
    }

    public void setColdCrashCheck(ColdCrashCheck coldCrashCheck) {
        this.coldCrashCheck = coldCrashCheck;
    }

    public SwitchedOffCheck getSwitchedOffCheck() {
        return switchedOffCheck;
    }

    public void setSwitchedOffCheck(SwitchedOffCheck switchedOffCheck) {
        this.switchedOffCheck = switchedOffCheck;
    }

    public StillAliveMessage getStillAliveMessage() {
        return stillAliveMessage;
    }

    public void setStillAliveMessage(StillAliveMessage stillAliveMessage) {
        this.stillAliveMessage = stillAliveMessage;
    }

    public Timezone getTimezone() {
        return timezone;
    }

    public void setTimezone(Timezone timezone) {
        this.timezone = timezone;
    }

    public PublicIpChange getPublicIpChange() {
        return publicIpChange;
    }

    public void setPublicIpChange(PublicIpChange publicIpChange) {
        this.publicIpChange = publicIpChange;
    }

    public static class PasswordHashes {
        private String guest;
        private String admin;

        public String getGuest() {
            return guest;
        }

        public void setGuest(String guest) {
            this.guest = guest;
        }

        public String getAdmin() {
            return admin;
        }

        public void setAdmin(String admin) {
            this.admin = admin;
        }
    }

    public static class Mail {
        private String from;
        private String to;

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }
    }

    public static class Readings {
        private int periodMillis = 60000;
        private int timestampResolutionMillis = 30000;
        private Gen gen = new Gen();
        private Temperature temp = new Temperature();
        private Optimise optimise = new Optimise();
        private boolean staggerFirstReadings = true;

        public int getPeriodMillis() {
            return periodMillis;
        }

        public void setPeriodMillis(int periodMillis) {
            this.periodMillis = periodMillis;
        }

        public int getTimestampResolutionMillis() {
            return timestampResolutionMillis;
        }

        public void setTimestampResolutionMillis(int timestampResolutionMillis) {
            this.timestampResolutionMillis = timestampResolutionMillis;
        }

        public Gen getGen() {
            return gen;
        }

        public void setGen(Gen gen) {
            this.gen = gen;
        }

        public Temperature getTemp() {
            return temp;
        }

        public void setTemp(Temperature temp) {
            this.temp = temp;
        }

        public Optimise getOptimise() {
            return optimise;
        }

        public void setOptimise(Optimise optimise) {
            this.optimise = optimise;
        }

        public boolean isStaggerFirstReadings() {
            return staggerFirstReadings;
        }

        public void setStaggerFirstReadings(boolean staggerFirstReadings) {
            this.staggerFirstReadings = staggerFirstReadings;
        }
    }

    public static class Gen {
        private int gen1ReadingsCount = 30;
        private int multiplier = 10;
        private int max = 4;

        public int getGen1ReadingsCount() {
            return gen1ReadingsCount;
        }

        public void setGen1ReadingsCount(int gen1ReadingsCount) {
            this.gen1ReadingsCount = gen1ReadingsCount;
        }

        public int getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(int multiplier) {
            this.multiplier = multiplier;
        }

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }
    }

    public static class Temperature {
        private Smoothing smoothing = new Smoothing();

        public Smoothing getSmoothing() {
            return smoothing;
        }

        public void setSmoothing(Smoothing smoothing) {
            this.smoothing = smoothing;
        }
    }

    public static class TempProfile {
        private int transitionHours = 3;

        public int getTransitionHours() {
            return transitionHours;
        }

        public void setTransitionHours(int transitionHours) {
            if (transitionHours < 0)
                throw new IllegalArgumentException("transitionHours must not be negative");
            this.transitionHours = transitionHours;
        }
    }

    public static class Smoothing {
        private int thresholdHeight = 2;
        private int[] thresholdWidths;
        public int getThresholdHeight() {
            return thresholdHeight;
        }

        public void setThresholdHeight(int thresholdHeight) {
            this.thresholdHeight = thresholdHeight;
        }

        public int[] getThresholdWidths() {
            return thresholdWidths;
        }

        public void setThresholdWidths(int[] thresholdWidths) {
            this.thresholdWidths = thresholdWidths;
        }
    }

    public static class Optimise {
        private boolean smoothTemperatureReadings = true;
        private boolean nullOutRedundantValues = true;
        private boolean removeRedundantIntermediate = true;
        public boolean isSmoothTemperatureReadings() {
            return smoothTemperatureReadings;
        }

        public void setSmoothTemperatureReadings(boolean smoothTemperatureReadings) {
            this.smoothTemperatureReadings = smoothTemperatureReadings;
        }

        public boolean isNullOutRedundantValues() {
            return nullOutRedundantValues;
        }

        public void setNullOutRedundantValues(boolean nullOutRedundantValues) {
            this.nullOutRedundantValues = nullOutRedundantValues;
        }

        public boolean isRemoveRedundantIntermediate() {
            return removeRedundantIntermediate;
        }

        public void setRemoveRedundantIntermediate(boolean removeRedundantIntermediate) {
            this.removeRedundantIntermediate = removeRedundantIntermediate;
        }
    }

    public static class ColdCrashCheck {
        private int periodMinutes = 30;
        private int priorNoticeHours = 36;
        private int postCrashDwellHours = 48;

        public int getPeriodMinutes() {
            return periodMinutes;
        }

        public void setPeriodMinutes(int periodMinutes) {
            this.periodMinutes = periodMinutes;
        }

        public int getPriorNoticeHours() {
            return priorNoticeHours;
        }

        public void setPriorNoticeHours(int priorNoticeHours) {
            this.priorNoticeHours = priorNoticeHours;
        }

        public int getPostCrashDwellHours() {
            return postCrashDwellHours;
        }

        public void setPostCrashDwellHours(int postCrashDwellHours) {
            this.postCrashDwellHours = postCrashDwellHours;
        }
    }

    public static class SwitchedOffCheck {
        private int periodMinutes = 5;
        private int ignoreFirstHours = 4;
        private int fridgeOnTimeMins = 30;
        private int heaterOnTimeMins = 15;

        public int getPeriodMinutes() {
            return periodMinutes;
        }

        public void setPeriodMinutes(int periodMinutes) {
            this.periodMinutes = periodMinutes;
        }

        public int getIgnoreFirstHours() {
            return ignoreFirstHours;
        }

        public void setIgnoreFirstHours(int ignoreFirstHours) {
            this.ignoreFirstHours = ignoreFirstHours;
        }

        public int getFridgeOnTimeMins() {
            return fridgeOnTimeMins;
        }

        public void setFridgeOnTimeMins(int fridgeOnTimeMins) {
            this.fridgeOnTimeMins = fridgeOnTimeMins;
        }

        public int getHeaterOnTimeMins() {
            return heaterOnTimeMins;
        }

        public void setHeaterOnTimeMins(int heaterOnTimeMins) {
            this.heaterOnTimeMins = heaterOnTimeMins;
        }
    }

    public static class StillAliveMessage {
        private String cronSchedule;

        public String getCronSchedule() {
            return cronSchedule;
        }

        public void setCronSchedule(String cronSchedule) {
            this.cronSchedule = cronSchedule;
        }
    }

    public static class Timezone {
        private String localId = "Europe/London";

        public String getLocalId() {
            return localId;
        }

        public void setLocalId(String localId) {
            this.localId = localId;
        }
    }

    public static class OfflineCheck {
        private String cronSchedule;
        private String routerAddress;
        private int bufferSizeHours = 24;

        public String getCronSchedule() {
            return cronSchedule;
        }

        public void setCronSchedule(String cronSchedule) {
            this.cronSchedule = cronSchedule;
        }

        public String getRouterAddress() {
            return routerAddress;
        }

        public void setRouterAddress(String routerAddress) {
            this.routerAddress = routerAddress;
        }

        public int getBufferSizeHours() {
            return bufferSizeHours;
        }

        public void setBufferSizeHours(int bufferSizeHours) {
            this.bufferSizeHours = bufferSizeHours;
        }
    }

    public static class PublicIpChange {
        private List<String> ipProviders = new ArrayList<>();
        private int periodHours;
        private String dnsUpdateUrl;

        public int getPeriodHours() {
            return periodHours;
        }

        public void setPeriodHours(int periodHours) {
            this.periodHours = periodHours;
        }

        public List<String> getIpProviders() {
            return ipProviders;
        }

        public void setIpProviders(List<String> ipProviders) {
            this.ipProviders = ipProviders;
        }

        public String getDnsUpdateUrl() {
            return dnsUpdateUrl;
        }

        public void setDnsUpdateUrl(String dnsUpdateUrl) {
            this.dnsUpdateUrl = dnsUpdateUrl;
        }
    }
}
