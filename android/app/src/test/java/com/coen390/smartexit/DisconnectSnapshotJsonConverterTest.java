package com.coen390.smartexit;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DisconnectSnapshotJsonConverterTest {

    @Test
    public void roundTripPreservesItemStatesPlatesAndTimestamp() {
        ItemProfile keys = new ItemProfile("keys", "Keys", 35.0, 45.0);
        ItemProfile wallet = new ItemProfile("wallet", "Wallet", 135.0, 155.0);
        DisconnectSnapshot original = DisconnectSnapshot.from(
                123456L,
                Arrays.asList(
                        TrackedItemState.present(keys, 3),
                        TrackedItemState.missing(wallet)
                )
        );

        DisconnectSnapshot restored = DisconnectSnapshotJsonConverter.fromJson(
                DisconnectSnapshotJsonConverter.toJson(original)
        );

        assertEquals(123456L, restored.getTimestampMillis());
        assertEquals(Arrays.asList("Keys"), restored.getPresentItemNames());

        List<TrackedItemState> states = restored.restore(Arrays.asList(keys, wallet));
        assertEquals(TrackedItemStatus.PRESENT, states.get(0).getStatus());
        assertEquals(Integer.valueOf(3), states.get(0).getPlateNumber());
        assertEquals(TrackedItemStatus.MISSING, states.get(1).getStatus());
        assertNull(states.get(1).getPlateNumber());
    }

    @Test
    public void malformedSnapshotIsIgnored() {
        assertNull(DisconnectSnapshotJsonConverter.fromJson("{not valid json"));
        assertNull(DisconnectSnapshotJsonConverter.fromJson(""));
        assertNull(DisconnectSnapshotJsonConverter.fromJson(null));
    }
}
