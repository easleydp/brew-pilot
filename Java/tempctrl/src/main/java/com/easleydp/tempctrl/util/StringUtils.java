package com.easleydp.tempctrl.util;

public class StringUtils
{
    public static String substringBetween(String str, String before, String after)
    {
        int i = str.indexOf(before);
        int j = str.indexOf(after, i + before.length());
        if (i == -1)
            throw new RuntimeException("String " + str + " did not contain " + before);
        if (j == -1)
            throw new RuntimeException("String " + str + " did not contain " + after);
        return str.substring(i + before.length(), j);
    }

    public static String substringAfter(String str, String before)
    {
        int i = str.indexOf(before);
        if (i == -1)
            throw new RuntimeException("String " + str + " did not contain " + before);
        return str.substring(i + before.length());
    }
}
