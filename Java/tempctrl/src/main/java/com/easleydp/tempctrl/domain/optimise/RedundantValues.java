package com.easleydp.tempctrl.domain.optimise;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.Assert;

public class RedundantValues
{
    private static final Logger logger = LoggerFactory.getLogger(RedundantValues.class);

    /**
     * For each bean property:
     * If some contiguous beans have a property P with same (non-null) value V
     * then null-out all the subsequent values.
     *
     * Note, we used to just null out the intermediate values (i.e. avoiding the last record in the
     * contiguous list). But, assuming the consumer (FE) knows the sampling period, there's no need
     * to preserve the last record in full.
     */
    public static void nullOutRedundantValues(List<?> beans, String propertyName)
    {
        int len = beans.size();

        // Scan forward looking for 2 or more beans with same (non-null) property value.
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
                    while (j - i > 0)
                    {
                        wrapper = new BeanWrapperImpl(beans.get(j--));
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

    public static void removeRedundantIntermediateBeans(List<?> beans, String[] nullablePropertyNames)
    {
        int startSize = beans.size();
        if (startSize < 3)
            return;
        for (int i = startSize - 2; i > 0; i--)
            if (allNullablePropertiesAreNull(beans.get(i), nullablePropertyNames))
                beans.remove(i);
        int endSize = beans.size();
        Assert.state(endSize >= 2, "Should always be left with at least the first & last beans");
        if (endSize < startSize)
            logger.debug("removed redundant intermediate beans: " + (startSize - endSize));
    }
    private static boolean allNullablePropertiesAreNull(Object bean, String[] nullablePropertyNames)
    {
        for (String propertyName : nullablePropertyNames)
            if (new BeanWrapperImpl(bean).getPropertyValue(propertyName) != null)
                return false;
        return true;
    }
}
