package com.coen390.smartexit;

import org.junit.Test;
import static org.junit.Assert.*;

public class ItemProfileTest {

    @Test
    public void newItem_isNotCalibrated() {
        ItemProfile item = new ItemProfile("Water Bottle");
        assertFalse(item.isCalibrated());
    }

    @Test
    public void afterSettingRange_isCalibrated() {
        ItemProfile item = new ItemProfile("Water Bottle");
        item.setWeightRange(480.0, 520.0);
        assertTrue(item.isCalibrated());
    }

    @Test
    public void matches_readingInsideRange() {
        ItemProfile item = new ItemProfile("Water Bottle");
        item.setWeightRange(480.0, 520.0);
        assertTrue(item.matches(500.0));
        assertFalse(item.matches(50.0));
    }
}