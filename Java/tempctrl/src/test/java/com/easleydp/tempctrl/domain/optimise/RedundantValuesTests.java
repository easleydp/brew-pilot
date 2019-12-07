package com.easleydp.tempctrl.domain.optimise;

import static com.easleydp.tempctrl.domain.optimise.RedundantValues.*;
import static com.easleydp.tempctrl.domain.optimise.RedundantValuesTests.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

public class RedundantValuesTests
{
    @Test
    public void test0()
    {
        ArrayList<Dto> dtos = new ArrayList<>();

        for (String propertyName : Dto.getNullablePropertyNames())
            RedundantValues.nullOutRedundantValues(dtos, propertyName);

        // Just checking it doesn't blow up!
    }

    @Test
    public void test1()
    {
        ArrayList<Dto> dtos = new ArrayList<>(Arrays.asList(
                new Dto(1, true, AUTO)
        ));

        for (String propertyName : Dto.getNullablePropertyNames())
            RedundantValues.nullOutRedundantValues(dtos, propertyName);

        assertEquals(new Dto(1, true, AUTO), dtos.get(0));
    }

    @Test
    public void test2()
    {
        ArrayList<Dto> dtos = new ArrayList<>(Arrays.asList(
                new Dto(1, true, AUTO),
                new Dto(1, true, AUTO)
        ));

        for (String propertyName : Dto.getNullablePropertyNames())
            RedundantValues.nullOutRedundantValues(dtos, propertyName);

        assertEquals(new Dto(1, true, AUTO), dtos.get(0));
        assertEquals(new Dto(1, true, AUTO), dtos.get(1));
    }

    @Test
    public void test3()
    {
        ArrayList<Dto> dtos = new ArrayList<>(Arrays.asList(
                new Dto(1, true, AUTO),
                new Dto(1, true, AUTO),
                new Dto(1, true, AUTO)
        ));

        for (String propertyName : Dto.getNullablePropertyNames())
            RedundantValues.nullOutRedundantValues(dtos, propertyName);

        assertEquals(new Dto(1, true, AUTO), dtos.get(0));
        assertEquals(new Dto(null, null, null), dtos.get(1));
        assertEquals(new Dto(1, true, AUTO), dtos.get(2));


        removeRedundantIntermediateBeans(dtos, Dto.getNullablePropertyNames());

        assertEquals(2, dtos.size());
        assertEquals(new Dto(1, true, AUTO), dtos.get(0));
        assertEquals(new Dto(1, true, AUTO), dtos.get(1));
    }

    @Test
    public void test4()
    {
        ArrayList<Dto> dtos = new ArrayList<>(Arrays.asList(
                new Dto(1, true, AUTO),
                new Dto(1, true, AUTO),
                new Dto(1, true, AUTO),
                new Dto(1, true, AUTO)
        ));

        for (String propertyName : Dto.getNullablePropertyNames())
            RedundantValues.nullOutRedundantValues(dtos, propertyName);

        assertEquals(new Dto(1, true, AUTO), dtos.get(0));
        assertEquals(new Dto(null, null, null), dtos.get(1));
        assertEquals(new Dto(null, null, null), dtos.get(2));
        assertEquals(new Dto(1, true, AUTO), dtos.get(3));


        removeRedundantIntermediateBeans(dtos, Dto.getNullablePropertyNames());

        assertEquals(2, dtos.size());
        assertEquals(new Dto(1, true, AUTO), dtos.get(0));
        assertEquals(new Dto(1, true, AUTO), dtos.get(1));
    }

    enum Mode { AUTO, HOLD }
    static class Dto
    {
        private Integer integer;
        private Boolean bool;
        private Mode mode;

        public Dto(Integer integer, Boolean bool, Mode mode)
        {
            this.integer = integer;
            this.bool = bool;
            this.mode = mode;
        }

        private static final String[] nullablePropertyNames = new String[] {
                "integer", "bool", "mode"
        };
        public static String[] getNullablePropertyNames()
        {
            return nullablePropertyNames;
        }

        public Integer getInteger()
        {
            return integer;
        }
        public void setInteger(Integer integer)
        {
            this.integer = integer;
        }
        public Boolean getBool()
        {
            return bool;
        }
        public void setBool(Boolean bool)
        {
            this.bool = bool;
        }
        public Mode getMode()
        {
            return mode;
        }
        public void setMode(Mode mode)
        {
            this.mode = mode;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((bool == null) ? 0 : bool.hashCode());
            result = prime * result + ((integer == null) ? 0 : integer.hashCode());
            result = prime * result + ((mode == null) ? 0 : mode.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Dto other = (Dto) obj;
            if (bool == null)
            {
                if (other.bool != null)
                    return false;
            }
            else if (!bool.equals(other.bool))
                return false;
            if (integer == null)
            {
                if (other.integer != null)
                    return false;
            }
            else if (!integer.equals(other.integer))
                return false;
            if (mode != other.mode)
                return false;
            return true;
        }

    }

}
