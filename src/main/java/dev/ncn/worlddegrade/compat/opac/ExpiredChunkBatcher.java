package dev.ncn.worlddegrade.compat.opac;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Collects the per-chunk {@code onChunkChange} callbacks OPAC fires when a claim expires into whole
 * batches, so a year-old server expiring thousands of chunks produces a handful of schedules instead
 * of thousands of one-chunk ones.
 *
 * <p>Deliberately free of any OPAC type — it deals only in {@link ResourceLocation} dimensions and
 * long-packed chunk keys — so its logic (per-dimension separation, the quiet-period debounce, and the
 * {@code maxChunks} split) is unit-testable without OPAC on the test classpath, the same reason
 * {@code DegradeSchedule} was split out from the scheduler.
 *
 * <p>Not thread-safe by design: every call happens on the server thread (OPAC's expiration task and
 * the server tick both run there).
 */
public final class ExpiredChunkBatcher {

    /**
     * A dimension's buffer is flushed once no new expired chunk has arrived for this many ticks (~2s).
     * Expiration spreads its per-chunk callbacks across many ticks, so this waits for the burst to
     * settle rather than flushing after every single chunk.
     */
    static final int DEBOUNCE_TICKS = 40;

    private final int maxChunks;
    private final Map<ResourceLocation, LongOpenHashSet> buffers = new HashMap<>();
    private final Map<ResourceLocation, Integer> lastTouched = new HashMap<>();

    public ExpiredChunkBatcher(int maxChunks) {
        this.maxChunks = maxChunks;
    }

    /** A ready batch: all chunks belong to one dimension and never exceed {@code maxChunks}. */
    public record Batch(ResourceLocation dimension, LongOpenHashSet chunks) {
    }

    public void add(ResourceLocation dimension, long packedChunk, int tick) {
        buffers.computeIfAbsent(dimension, d -> new LongOpenHashSet()).add(packedChunk);
        lastTouched.put(dimension, tick);
    }

    public boolean isEmpty() {
        return buffers.isEmpty();
    }

    /**
     * Batches whose dimension has gone quiet for {@link #DEBOUNCE_TICKS}, plus full {@code maxChunks}
     * batches carved off any dimension that has overflowed while still receiving chunks. A dimension
     * that is neither quiet nor overflowing keeps buffering.
     */
    public List<Batch> drainReady(int now) {
        List<Batch> out = new ArrayList<>();
        Iterator<Map.Entry<ResourceLocation, LongOpenHashSet>> it = buffers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceLocation, LongOpenHashSet> e = it.next();
            ResourceLocation dimension = e.getKey();
            LongOpenHashSet set = e.getValue();
            if (now - lastTouched.get(dimension) >= DEBOUNCE_TICKS) {
                splitAll(dimension, set, out);
                it.remove();
                lastTouched.remove(dimension);
            } else {
                // Still receiving chunks, but bail out full batches now so a very large expiration does
                // not grow one buffer without bound; the remainder keeps debouncing.
                while (set.size() >= maxChunks) {
                    out.add(new Batch(dimension, take(set, maxChunks)));
                }
            }
        }
        return out;
    }

    /** Everything currently buffered, ignoring the debounce — for a clean flush at shutdown. */
    public List<Batch> drainAll() {
        List<Batch> out = new ArrayList<>();
        for (Map.Entry<ResourceLocation, LongOpenHashSet> e : buffers.entrySet()) {
            splitAll(e.getKey(), e.getValue(), out);
        }
        buffers.clear();
        lastTouched.clear();
        return out;
    }

    private void splitAll(ResourceLocation dimension, LongOpenHashSet set, List<Batch> out) {
        while (!set.isEmpty()) {
            out.add(new Batch(dimension, take(set, maxChunks)));
        }
    }

    /** Removes up to {@code count} chunks from {@code set} and returns them as a new set. */
    private static LongOpenHashSet take(LongOpenHashSet set, int count) {
        LongOpenHashSet taken = new LongOpenHashSet(Math.min(count, set.size()));
        LongIterator it = set.iterator();
        while (it.hasNext() && taken.size() < count) {
            taken.add(it.nextLong());
            it.remove();
        }
        return taken;
    }
}
