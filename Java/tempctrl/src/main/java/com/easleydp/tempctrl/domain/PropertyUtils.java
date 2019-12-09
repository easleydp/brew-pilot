package com.easleydp.tempctrl.domain;

import java.util.stream.Stream;

import org.springframework.core.env.Environment;

public class PropertyUtils
{

    // Get required
    public static String getString(Environment env, String key)
    {
        return env.getRequiredProperty(key, String.class);
    }
    // Get default
    public static String getString(Environment env, String key, String defaultValue)
    {
        return env.getProperty(key, String.class, defaultValue);
    }


    // Get required
    public static String[] getStringArray(Environment env, String key)
    {
        String[] array = getStringArray(env, key, null);
        checkRequired(key, array, true);
        return array;
    }
    // Get default
    public static String[] getStringArray(Environment env, String key, String[] defaultValue)
    {
        String raw = env.getProperty(key);
        // https://stackoverflow.com/a/53135316/65555
        String[] array = raw.replaceAll("[\\[\\]\\ ]", "").split(",");
        return array != null ? array : defaultValue;
    }


    // Get required
    public static boolean getBoolean(Environment env, String key)
    {
        return env.getRequiredProperty(key, Boolean.class);
    }
    // Get default
    public static Boolean getBoolean(Environment env, String key, Boolean defaultValue)
    {
        return env.getProperty(key, Boolean.class, defaultValue);
    }


    // Get required
    public static int getInt(Environment env, String key)
    {
        return env.getRequiredProperty(key, Integer.class);
    }
    // Get default
    public static Integer getInteger(Environment env, String key, Integer defaultValue)
    {
        return env.getProperty(key, Integer.class, defaultValue);
    }


    // Get required
    public static int[] getIntArray(Environment env, String key)
    {
        int[] array = getIntArray(env, key, null);
        checkRequired(key, array, true);
        return array;
    }
    // Get default
    public static int[] getIntArray(Environment env, String key, int[] defaultValue)
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

}
