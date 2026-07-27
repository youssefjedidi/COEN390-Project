package com.coen390.smartexit;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class ItemProfileRepository {

    private static final String PREFS_NAME = "item_profiles_prefs";
    private static final String KEY_PROFILES = "item_profiles_json";

    private final SharedPreferences prefs;

    public ItemProfileRepository(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<ItemProfile> getAll() {
        String json = prefs.getString(KEY_PROFILES, null);
        return ItemProfileJsonConverter.fromJson(json);
    }

    public void saveAll(List<ItemProfile> profiles) {
        String json = ItemProfileJsonConverter.toJson(profiles);
        prefs.edit().putString(KEY_PROFILES, json).apply();
    }

    public void save(ItemProfile profile) {
        List<ItemProfile> all = getAll();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(profile.getId())) {
                all.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            all.add(profile);
        }
        saveAll(all);
    }

    public void delete(String id) {
        List<ItemProfile> all = getAll();
        List<ItemProfile> remaining = new ArrayList<>();
        for (ItemProfile p : all) {
            if (!p.getId().equals(id)) {
                remaining.add(p);
            }
        }
        saveAll(remaining);
    }
}