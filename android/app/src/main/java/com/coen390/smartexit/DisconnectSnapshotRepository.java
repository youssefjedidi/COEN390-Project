package com.coen390.smartexit;

import android.content.Context;
import android.content.SharedPreferences;

final class DisconnectSnapshotRepository {

    private static final String PREFS_NAME = "disconnect_snapshot_prefs";
    private static final String KEY_SNAPSHOT = "last_disconnect_snapshot";

    private final SharedPreferences preferences;

    DisconnectSnapshotRepository(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    void save(DisconnectSnapshot snapshot) {
        preferences.edit()
                .putString(KEY_SNAPSHOT, DisconnectSnapshotJsonConverter.toJson(snapshot))
                .apply();
    }

    DisconnectSnapshot load() {
        return DisconnectSnapshotJsonConverter.fromJson(
                preferences.getString(KEY_SNAPSHOT, null)
        );
    }
}
