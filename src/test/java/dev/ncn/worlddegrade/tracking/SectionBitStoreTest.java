package dev.ncn.worlddegrade.tracking;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionBitStoreTest {

    private static long pos(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    private static long[] sorted(long[] values) {
        long[] copy = values.clone();
        Arrays.sort(copy);
        return copy;
    }

    @Test
    void roundTripsPositiveAndNegativeCoordinates() {
        SectionBitStore store = new SectionBitStore();
        long[] input = {
                pos(0, 0, 0),
                pos(15, 15, 15),
                pos(-100, -48, 200),
                pos(-1, -1, -1),
                pos(1234, 63, -5678),
        };
        for (long p : input) {
            store.add(p);
        }
        assertArrayEquals(sorted(input), sorted(store.toLongArray()));
    }

    @Test
    void containsReportsStoredAndUnstored() {
        SectionBitStore store = new SectionBitStore();
        store.add(pos(3, 4, 5));
        assertTrue(store.contains(pos(3, 4, 5)));
        assertFalse(store.contains(pos(3, 4, 6)));
        assertFalse(store.contains(pos(-3, -4, -5)));
    }

    @Test
    void sectionBoundaryDoesNotAlias() {
        SectionBitStore store = new SectionBitStore();
        store.add(pos(15, 0, 0));
        // x=16 sits in the next section and must be a distinct entry, not an alias of x=15.
        assertTrue(store.contains(pos(15, 0, 0)));
        assertFalse(store.contains(pos(16, 0, 0)));
        store.add(pos(16, 0, 0));
        assertArrayEquals(sorted(new long[]{pos(15, 0, 0), pos(16, 0, 0)}), sorted(store.toLongArray()));
    }

    @Test
    void removeUntilEmptyPrunesSections() {
        SectionBitStore store = new SectionBitStore();
        long a = pos(2, 2, 2);
        long b = pos(2, 3, 2);
        store.add(a);
        store.add(b);
        assertFalse(store.isEmpty());
        store.remove(a);
        store.remove(b);
        assertTrue(store.isEmpty());
        assertArrayEquals(new long[0], store.toLongArray());
    }

    @Test
    void codecRoundTripPreservesPositions() {
        SectionBitStore store = new SectionBitStore();
        long[] input = {pos(0, 0, 0), pos(15, 15, 15), pos(-100, -48, 200), pos(50, 200, -50)};
        for (long p : input) {
            store.add(p);
        }
        JsonElement encoded = SectionBitStore.CODEC.encodeStart(JsonOps.INSTANCE, store).getOrThrow();
        SectionBitStore decoded = SectionBitStore.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertArrayEquals(sorted(input), sorted(decoded.toLongArray()));
    }

    @Test
    void parsesLegacyFlatLongArray() {
        long[] input = {pos(1, 2, 3), pos(-4, -5, -6), pos(100, 60, 100)};
        JsonElement legacy = Codec.LONG_STREAM.encodeStart(JsonOps.INSTANCE, LongStream.of(input)).getOrThrow();
        SectionBitStore decoded = SectionBitStore.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertArrayEquals(sorted(input), sorted(decoded.toLongArray()));
    }

    @Test
    void skipsOversizedWordsArray() {
        // A section whose words array exceeds 64 longs is corrupt: reject it instead of aliasing
        // bit indexes >= 4096 onto real positions.
        JsonArray words = new JsonArray();
        for (int i = 0; i < 65; i++) {
            words.add(new JsonPrimitive(i == 64 ? 1L : 0L));
        }
        JsonObject entry = new JsonObject();
        entry.add("section", new JsonPrimitive(0L));
        entry.add("words", words);
        JsonArray list = new JsonArray();
        list.add(entry);

        SectionBitStore decoded = SectionBitStore.CODEC.parse(JsonOps.INSTANCE, list).getOrThrow();
        assertTrue(decoded.isEmpty());
        assertArrayEquals(new long[0], decoded.toLongArray());
    }
}
