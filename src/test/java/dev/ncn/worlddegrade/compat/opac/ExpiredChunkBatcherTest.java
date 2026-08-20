package dev.ncn.worlddegrade.compat.opac;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpiredChunkBatcherTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation NETHER = ResourceLocation.parse("minecraft:the_nether");

    private static long chunk(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    @Test
    void nothingIsReadyBeforeTheDebounceWindowElapses() {
        ExpiredChunkBatcher batcher = new ExpiredChunkBatcher(10_000);
        batcher.add(OVERWORLD, chunk(0, 0), 100);
        // Just short of the quiet window: the burst may still be arriving.
        assertTrue(batcher.drainReady(100 + ExpiredChunkBatcher.DEBOUNCE_TICKS - 1).isEmpty());
        assertFalse(batcher.isEmpty());
    }

    @Test
    void aQuietDimensionFlushesOnceTheWindowElapses() {
        ExpiredChunkBatcher batcher = new ExpiredChunkBatcher(10_000);
        batcher.add(OVERWORLD, chunk(0, 0), 100);
        batcher.add(OVERWORLD, chunk(1, 0), 100);
        List<ExpiredChunkBatcher.Batch> ready =
                batcher.drainReady(100 + ExpiredChunkBatcher.DEBOUNCE_TICKS);
        assertEquals(1, ready.size());
        assertEquals(OVERWORLD, ready.get(0).dimension());
        assertEquals(2, ready.get(0).chunks().size());
        // Draining removed it entirely.
        assertTrue(batcher.isEmpty());
    }

    @Test
    void furtherChunksResetTheQuietWindow() {
        ExpiredChunkBatcher batcher = new ExpiredChunkBatcher(10_000);
        batcher.add(OVERWORLD, chunk(0, 0), 100);
        // A new chunk at tick 130 pushes the deadline out to 130 + DEBOUNCE.
        batcher.add(OVERWORLD, chunk(1, 0), 130);
        assertTrue(batcher.drainReady(100 + ExpiredChunkBatcher.DEBOUNCE_TICKS).isEmpty());
        assertEquals(1, batcher.drainReady(130 + ExpiredChunkBatcher.DEBOUNCE_TICKS).size());
    }

    @Test
    void dimensionsAreBatchedAndFlushedIndependently() {
        ExpiredChunkBatcher batcher = new ExpiredChunkBatcher(10_000);
        batcher.add(OVERWORLD, chunk(0, 0), 100);
        batcher.add(NETHER, chunk(0, 0), 140);
        // Only the overworld has gone quiet at this point.
        List<ExpiredChunkBatcher.Batch> ready =
                batcher.drainReady(100 + ExpiredChunkBatcher.DEBOUNCE_TICKS);
        assertEquals(1, ready.size());
        assertEquals(OVERWORLD, ready.get(0).dimension());
        assertFalse(batcher.isEmpty());
    }

    @Test
    void overflowingDimensionEmitsFullBatchesWhileStillReceiving() {
        ExpiredChunkBatcher batcher = new ExpiredChunkBatcher(4);
        for (int i = 0; i < 10; i++) {
            batcher.add(OVERWORLD, chunk(i, 0), 100);
        }
        // Well within the debounce window, so the dimension is not quiet — but it overflowed.
        List<ExpiredChunkBatcher.Batch> ready = batcher.drainReady(100 + 1);
        assertEquals(2, ready.size());
        assertEquals(4, ready.get(0).chunks().size());
        assertEquals(4, ready.get(1).chunks().size());
        // The remainder (2 chunks) keeps buffering until quiet.
        assertFalse(batcher.isEmpty());
        List<ExpiredChunkBatcher.Batch> tail =
                batcher.drainReady(100 + ExpiredChunkBatcher.DEBOUNCE_TICKS);
        assertEquals(1, tail.size());
        assertEquals(2, tail.get(0).chunks().size());
    }

    @Test
    void drainAllFlushesEverythingSplitByMaxChunks() {
        ExpiredChunkBatcher batcher = new ExpiredChunkBatcher(4);
        for (int i = 0; i < 5; i++) {
            batcher.add(OVERWORLD, chunk(i, 0), 100);
        }
        batcher.add(NETHER, chunk(0, 0), 100);
        List<ExpiredChunkBatcher.Batch> all = batcher.drainAll();
        // Overworld splits into 4 + 1; nether is a single chunk => three batches total.
        assertEquals(3, all.size());
        int total = all.stream().mapToInt(b -> b.chunks().size()).sum();
        assertEquals(6, total);
        assertTrue(all.stream().allMatch(b -> b.chunks().size() <= 4));
        assertTrue(batcher.isEmpty());
    }

    @Test
    void batchesNeverExceedMaxChunksAndCoverEveryAddedChunk() {
        ExpiredChunkBatcher batcher = new ExpiredChunkBatcher(3);
        LongOpenHashSet expected = new LongOpenHashSet();
        for (int i = 0; i < 7; i++) {
            long c = chunk(i, i);
            batcher.add(OVERWORLD, c, 100);
            expected.add(c);
        }
        LongOpenHashSet seen = new LongOpenHashSet();
        for (ExpiredChunkBatcher.Batch batch : batcher.drainAll()) {
            assertTrue(batch.chunks().size() <= 3);
            seen.addAll(batch.chunks());
        }
        assertEquals(expected, seen);
    }
}
