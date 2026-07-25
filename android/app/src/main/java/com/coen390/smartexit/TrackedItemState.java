package com.coen390.smartexit;

public final class TrackedItemState {

    private final ItemProfile item;
    private final TrackedItemStatus status;
    private final Integer plateNumber;

    private TrackedItemState(
            ItemProfile item,
            TrackedItemStatus status,
            Integer plateNumber
    ) {
        this.item = item;
        this.status = status;
        this.plateNumber = plateNumber;
    }

    static TrackedItemState present(ItemProfile item, int plateNumber) {
        return new TrackedItemState(item, TrackedItemStatus.PRESENT, plateNumber);
    }

    static TrackedItemState missing(ItemProfile item) {
        return new TrackedItemState(item, TrackedItemStatus.MISSING, null);
    }

    static TrackedItemState unknown(ItemProfile item) {
        return new TrackedItemState(item, TrackedItemStatus.UNKNOWN, null);
    }

    public ItemProfile getItem() {
        return item;
    }

    public TrackedItemStatus getStatus() {
        return status;
    }

    public Integer getPlateNumber() {
        return plateNumber;
    }
}
