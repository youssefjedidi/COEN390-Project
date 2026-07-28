package com.coen390.smartexit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class DisconnectSnapshotJsonConverter {

    private DisconnectSnapshotJsonConverter() {
    }

    static String toJson(DisconnectSnapshot snapshot) {
        try {
            JSONObject root = new JSONObject();
            root.put("timestampMillis", snapshot.getTimestampMillis());

            JSONArray items = new JSONArray();
            for (DisconnectSnapshot.ItemEntry item : snapshot.getItems()) {
                JSONObject value = new JSONObject();
                value.put("itemId", item.getItemId());
                value.put("itemName", item.getItemName());
                value.put("status", item.getStatus().name());
                if (item.getPlateNumber() != null) {
                    value.put("plateNumber", item.getPlateNumber());
                }
                items.put(value);
            }
            root.put("items", items);
            return root.toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Could not save the disconnect snapshot", error);
        }
    }

    static DisconnectSnapshot fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        try {
            JSONObject root = new JSONObject(json);
            JSONArray values = root.getJSONArray("items");
            List<DisconnectSnapshot.ItemEntry> items = new ArrayList<>();

            for (int index = 0; index < values.length(); index++) {
                JSONObject value = values.getJSONObject(index);
                Integer plateNumber = value.has("plateNumber")
                        ? value.getInt("plateNumber")
                        : null;
                items.add(
                        new DisconnectSnapshot.ItemEntry(
                                value.getString("itemId"),
                                value.getString("itemName"),
                                TrackedItemStatus.valueOf(value.getString("status")),
                                plateNumber
                        )
                );
            }
            return new DisconnectSnapshot(root.getLong("timestampMillis"), items);
        } catch (JSONException | IllegalArgumentException error) {
            return null;
        }
    }
}
