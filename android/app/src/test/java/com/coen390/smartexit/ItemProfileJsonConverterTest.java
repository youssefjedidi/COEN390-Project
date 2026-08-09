package com.coen390.smartexit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ItemProfileJsonConverterTest {

    @Test
    public void emptyList_roundTripsToEmptyList() {
        String json = ItemProfileJsonConverter.toJson(new ArrayList<>());
        List<ItemProfile> result = ItemProfileJsonConverter.fromJson(json);
        assertTrue(result.isEmpty());
    }

    @Test
    public void uncalibratedProfile_roundTripsCorrectly() {
        ItemProfile original = new ItemProfile("Water Bottle");
        String json = ItemProfileJsonConverter.toJson(List.of(original));
        List<ItemProfile> result = ItemProfileJsonConverter.fromJson(json);
        assertEquals(1, result.size());
        ItemProfile loaded = result.get(0);
        assertEquals(original.getId(), loaded.getId());
        assertEquals("Water Bottle", loaded.getName());
        assertFalse(loaded.isCalibrated());
    }

    @Test
    public void calibratedProfile_roundTripsWithWeightRange() {
        ItemProfile original = new ItemProfile("Water Bottle");
        original.setWeightRange(480.0, 520.0);
        String json = ItemProfileJsonConverter.toJson(List.of(original));
        List<ItemProfile> result = ItemProfileJsonConverter.fromJson(json);
        ItemProfile loaded = result.get(0);
        assertTrue(loaded.isCalibrated());
        assertEquals(480.0, loaded.getMinWeightGrams(), 0.001);
        assertEquals(520.0, loaded.getMaxWeightGrams(), 0.001);
    }

    @Test
    public void multipleProfiles_allPersistCorrectly() {
        ItemProfile a = new ItemProfile("Water Bottle");
        a.setWeightRange(480.0, 520.0);
        ItemProfile b = new ItemProfile("Keys");
        String json = ItemProfileJsonConverter.toJson(List.of(a, b));
        List<ItemProfile> result = ItemProfileJsonConverter.fromJson(json);
        assertEquals(2, result.size());
        assertEquals("Water Bottle", result.get(0).getName());
        assertEquals("Keys", result.get(1).getName());
    }

    @Test
    public void malformedJson_returnsEmptyListInsteadOfCrashing() {
        List<ItemProfile> result = ItemProfileJsonConverter.fromJson("{not valid json");
        assertTrue(result.isEmpty());
    }

    @Test
    public void invalidSavedProfile_doesNotHideValidProfiles() {
        String json = "["
                + "{\"id\":\"keys\",\"name\":\"Keys\","
                + "\"minWeightGrams\":35.0,\"maxWeightGrams\":45.0},"
                + "{\"id\":\"broken\",\"name\":\"Broken\","
                + "\"minWeightGrams\":80.0,\"maxWeightGrams\":20.0}"
                + "]";

        List<ItemProfile> result = ItemProfileJsonConverter.fromJson(json);

        assertEquals(1, result.size());
        assertEquals("Keys", result.get(0).getName());
    }

    @Test
    public void nullOrBlankJson_returnsEmptyList() {
        assertTrue(ItemProfileJsonConverter.fromJson(null).isEmpty());
        assertTrue(ItemProfileJsonConverter.fromJson("").isEmpty());
    }
}
