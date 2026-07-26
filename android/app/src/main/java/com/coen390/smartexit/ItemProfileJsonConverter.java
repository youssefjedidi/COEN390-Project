package com.coen390.smartexit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ItemProfileJsonConverter {

    private ItemProfileJsonConverter() {
    }

    public static String toJson(List<ItemProfile> profiles) {
        JSONArray array = new JSONArray();
        for (ItemProfile profile : profiles) {
            array.put(profileToJson(profile));
        }
        return array.toString();
    }

    public static List<ItemProfile> fromJson(String json) {
        List<ItemProfile> result = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                result.add(profileFromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return result;
    }

    private static JSONObject profileToJson(ItemProfile profile) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", profile.getId());
            obj.put("name", profile.getName());
            if (profile.isCalibrated()) {
                obj.put("minWeightGrams", profile.getMinWeightGrams());
                obj.put("maxWeightGrams", profile.getMaxWeightGrams());
            }
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to serialize ItemProfile: " + profile, e);
        }
        return obj;
    }

    private static ItemProfile profileFromJson(JSONObject obj) throws JSONException {
        String id = obj.getString("id");
        String name = obj.getString("name");
        Double min = obj.has("minWeightGrams") ? obj.getDouble("minWeightGrams") : null;
        Double max = obj.has("maxWeightGrams") ? obj.getDouble("maxWeightGrams") : null;
        return new ItemProfile(id, name, min, max);
    }
}