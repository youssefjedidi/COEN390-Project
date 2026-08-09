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

    @Test
    public void weightRange_rejectsInvalidValues() {
        ItemProfile item = new ItemProfile("Water Bottle");

        assertInvalidRange(item, -1.0, 10.0);
        assertInvalidRange(item, 20.0, 10.0);
        assertInvalidRange(item, Double.NaN, 10.0);
        assertInvalidRange(item, 10.0, Double.POSITIVE_INFINITY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void restoredProfile_rejectsOnlyOneWeightBoundary() {
        new ItemProfile("bottle", "Water Bottle", 480.0, null);
    }

    private void assertInvalidRange(ItemProfile item, double minimum, double maximum) {
        assertThrows(
                IllegalArgumentException.class,
                () -> item.setWeightRange(minimum, maximum)
        );
    }
}
