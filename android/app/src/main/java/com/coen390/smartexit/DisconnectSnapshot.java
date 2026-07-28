package com.coen390.smartexit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DisconnectSnapshot {

    static final class ItemEntry {
        private final String itemId;
        private final String itemName;
        private final TrackedItemStatus status;
        private final Integer plateNumber;

        ItemEntry(
                String itemId,
                String itemName,
                TrackedItemStatus status,
                Integer plateNumber
        ) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.status = status;
            this.plateNumber = plateNumber;
        }

        String getItemId() {
            return itemId;
        }

        String getItemName() {
            return itemName;
        }

        TrackedItemStatus getStatus() {
            return status;
        }

        Integer getPlateNumber() {
            return plateNumber;
        }
    }

    private final long timestampMillis;
    private final List<ItemEntry> items;

    DisconnectSnapshot(long timestampMillis, List<ItemEntry> items) {
        this.timestampMillis = timestampMillis;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    static DisconnectSnapshot from(long timestampMillis, List<TrackedItemState> states) {
        List<ItemEntry> entries = new ArrayList<>();
        for (TrackedItemState state : states) {
            entries.add(
                    new ItemEntry(
                            state.getItem().getId(),
                            state.getItem().getName(),
                            state.getStatus(),
                            state.getPlateNumber()
                    )
            );
        }
        return new DisconnectSnapshot(timestampMillis, entries);
    }

    long getTimestampMillis() {
        return timestampMillis;
    }

    List<ItemEntry> getItems() {
        return items;
    }

    List<String> getPresentItemNames() {
        List<String> names = new ArrayList<>();
        for (ItemEntry item : items) {
            if (item.getStatus() == TrackedItemStatus.PRESENT) {
                names.add(item.getItemName());
            }
        }
        return names;
    }

    List<TrackedItemState> restore(List<ItemProfile> profiles) {
        List<TrackedItemState> states = new ArrayList<>();
        for (ItemProfile profile : profiles) {
            ItemEntry savedItem = findItem(profile.getId());
            states.add(savedItem == null ? TrackedItemState.unknown(profile) : restore(profile, savedItem));
        }
        return states;
    }

    private ItemEntry findItem(String itemId) {
        for (ItemEntry item : items) {
            if (item.getItemId().equals(itemId)) {
                return item;
            }
        }
        return null;
    }

    private TrackedItemState restore(ItemProfile profile, ItemEntry item) {
        if (item.getStatus() == TrackedItemStatus.PRESENT && item.getPlateNumber() != null) {
            return TrackedItemState.present(profile, item.getPlateNumber());
        }
        if (item.getStatus() == TrackedItemStatus.MISSING) {
            return TrackedItemState.missing(profile);
        }
        return TrackedItemState.unknown(profile);
    }
}
