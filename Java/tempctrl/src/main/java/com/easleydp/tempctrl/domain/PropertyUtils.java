package com.easleydp.tempctrl.domain;

import java.util.stream.Stream;

import org.springframework.core.env.Environment;

public class PropertyUtils
{
    // Not really the Spring way but we inject the Environment statically, to save clients having to supply it.
    private static Environment env;
    public static void setEnv(Environment env) {
        PropertyUtils.env = env;
    }

    // Get required
    public static String getString(String key)
    {
        return env.getRequiredProperty(key, String.class);
    }
    // Get default
    public static String getString(String key, String defaultValue)
    {
        return env.getProperty(key, String.class, defaultValue);
    }


    // Get required
    public static String[] getStringArray(String key)
    {
        String[] array = getStringArray(key, null);
        checkRequired(key, array, true);
        return array;
    }
    // Get default
    public static String[] getStringArray(String key, String[] defaultValue)
    {
        String raw = env.getProperty(key);
        // https://stackoverflow.com/a/53135316/65555
        String[] array = raw.replaceAll("[\\[\\]\\ ]", "").split(",");
        return array != null ? array : defaultValue;
    }


    // Get required
    public static boolean getBoolean(String key)
    {
        return env.getRequiredProperty(key, Boolean.class);
    }
    // Get default
    public static Boolean getBoolean(String key, Boolean defaultValue)
    {
        return env.getProperty(key, Boolean.class, defaultValue);
    }


    // Get required
    public static int getInt(String key)
    {
        return env.getRequiredProperty(key, Integer.class);
    }
    // Get default
    public static Integer getInteger(String key, Integer defaultValue)
    {
        return env.getProperty(key, Integer.class, defaultValue);
    }


    // Get required
    public static int[] getIntArray(String key)
    {
        int[] array = getIntArray(key, null);
        checkRequired(key, array, true);
        return array;
    }
    // Get default
    public static int[] getIntArray(String key, int[] defaultValue)
    {
        String raw = env.getProperty(key);
        if (raw == null)
            return null;
        // https://stackoverflow.com/a/53135316/65555
        return Stream.of(raw.replaceAll("[\\[\\]\\ ]", "").split(",")).mapToInt(Integer::parseInt).toArray();
    }



    private static void checkRequired(String key, Object value, boolean required)
    {
        if (value == null  &&  required)
            throw new IllegalStateException("Required property '" + key + "' is not defined.");
    }


    /*
     * Some blessed properties are made accessible by name. Part of the convenience is we can
     * encapsulate the default values.
     */

    public static int getReadingsTimestampResolutionMillis()
    {
        return getInteger("readings.timestamp.resolutionMillis", 30 * 1000);
    }

    public static int getReadingsPeriodMillis()
    {
        return getInteger("readings.periodMillis", 60 * 1000);
    }

}
