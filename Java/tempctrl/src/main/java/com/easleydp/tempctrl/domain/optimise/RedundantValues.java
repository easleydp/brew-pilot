package com.easleydp.tempctrl.domain.optimise;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.Assert;

public class RedundantValues
{
    /**
     * For each bean property:
     * If some contiguous beans have a property P with same (non-null) value V
     * then null-out all the intermediate values.
     */
    public static void nullOutRedundantValues(List<?> beans, String propertyName)
    {
        int len = beans.size();

        // Scan forward looking for 3 or more beans with same (non-null) property value.
        // i will be the index of the first record, j the last.
        int i = 0;
        int j = -1;
        Object prevValue = null;
        int currIndex = i;
        while (currIndex < len)
        {
            BeanWrapper wrapper = new BeanWrapperImpl(beans.get(currIndex));
            Object value = wrapper.getPropertyValue(propertyName);
            if (prevValue != null)
            {
                boolean valueHasChanged = !prevValue.equals(value);
                if (!valueHasChanged)
                {
                    j = currIndex;
                }

                if (valueHasChanged || currIndex + 1 == len)
                {
                    while (j - i > 1)
                    {
                        wrapper = new BeanWrapperImpl(beans.get(--j));
                        wrapper.setPropertyValue(propertyName, null);
                    }
                    i = currIndex;
                    j = -1;
                }
            }
            prevValue = value;
            currIndex++;
        }
    }

    public static void removeRedundantIntermediateBeans(ArrayList<?> beans, String[] nullablePropertyNames)
    {
        for (int i = beans.size() - 2; i > 0; i--)
            if (allNullablePropertiesAreNull(beans.get(i), nullablePropertyNames))
                beans.remove(i);
        Assert.state(beans.size() >= 2, "Should always be left with at least the first & last beans");
    }
    private static boolean allNullablePropertiesAreNull(Object bean, String[] nullablePropertyNames)
    {
        for (String propertyName : nullablePropertyNames)
            if (new BeanWrapperImpl(bean).getPropertyValue(propertyName) != null)
                return false;
        return true;
    }
}
