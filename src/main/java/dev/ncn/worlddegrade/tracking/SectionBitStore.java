package dev.ncn.worlddegrade.tracking;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.stream.LongStream;

/**
 * Stores player-touched block positions as one {@link BitSet} of 4096 bits per 16x16x16 chunk
 * section, keyed by {@link SectionPos} long. A bitset is only allocated on the first write into a
 * section, and pruned again once emptied, so sections with no activity cost nothing.
 *
 * <p><b>Memory crossover.</b> Each occupied section costs a fixed ~575 bytes (512-byte word array
 * plus {@code BitSet}/map-slot overhead) regardless of how many blocks it holds. Compared with a
 * flat {@code long} set at ~10.7 bytes per element, the break-even is roughly <b>54 blocks per
 * section</b>: above that this store wins (up to ~30-50x for dense factory bases), below it the
 * flat set is smaller (up to ~54x at a single block per section). This layout therefore favours
 * dense placement; sparse, scattered workloads (e.g. individually placed torches or rails) are a
 * regression on memory. Keep that crossover in mind before reusing this store for sparse data.
 */
public final class SectionBitStore {

    private static final int BITS_PER_SECTION = 4096;
    private static final int WORDS_PER_SECTION = BITS_PER_SECTION / Long.SIZE;

    private final Long2ObjectOpenHashMap<BitSet> sections = new Long2ObjectOpenHashMap<>();

    /** Bit index within a section, matching Minecraft's internal {@code (y << 8) | (z << 4) | x} layout. */
    private static int localIndex(int x, int y, int z) {
        return (x & 15) | ((z & 15) << 4) | ((y & 15) << 8);
    }

    private static long sectionKey(int x, int y, int z) {
        return SectionPos.asLong(
                SectionPos.blockToSectionCoord(x),
                SectionPos.blockToSectionCoord(y),
                SectionPos.blockToSectionCoord(z));
    }

    public void add(long blockPos) {
        int x = BlockPos.getX(blockPos);
        int y = BlockPos.getY(blockPos);
        int z = BlockPos.getZ(blockPos);
        long section = sectionKey(x, y, z);
        BitSet bits = sections.get(section);
        if (bits == null) {
            bits = new BitSet(BITS_PER_SECTION);
            sections.put(section, bits);
        }
        bits.set(localIndex(x, y, z));
    }

    public void remove(long blockPos) {
        int x = BlockPos.getX(blockPos);
        int y = BlockPos.getY(blockPos);
        int z = BlockPos.getZ(blockPos);
        long section = sectionKey(x, y, z);
        BitSet bits = sections.get(section);
        if (bits == null) {
            return;
        }
        bits.clear(localIndex(x, y, z));
        if (bits.isEmpty()) {
            sections.remove(section);
        }
    }

    public boolean contains(long blockPos) {
        int x = BlockPos.getX(blockPos);
        int y = BlockPos.getY(blockPos);
        int z = BlockPos.getZ(blockPos);
        BitSet bits = sections.get(sectionKey(x, y, z));
        return bits != null && bits.get(localIndex(x, y, z));
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    /** Reconstructs the absolute {@link BlockPos#asLong()} values of every set bit. */
    public long[] toLongArray() {
        int total = 0;
        for (BitSet bits : sections.values()) {
            total += bits.cardinality();
        }
        LongArrayList out = new LongArrayList(total);
        for (Long2ObjectMap.Entry<BitSet> entry : sections.long2ObjectEntrySet()) {
            long section = entry.getLongKey();
            int originX = SectionPos.sectionToBlockCoord(SectionPos.x(section));
            int originY = SectionPos.sectionToBlockCoord(SectionPos.y(section));
            int originZ = SectionPos.sectionToBlockCoord(SectionPos.z(section));
            BitSet bits = entry.getValue();
            for (int index = bits.nextSetBit(0); index >= 0; index = bits.nextSetBit(index + 1)) {
                int localX = index & 15;
                int localZ = (index >> 4) & 15;
                int localY = (index >> 8) & 15;
                out.add(BlockPos.asLong(originX + localX, originY + localY, originZ + localZ));
            }
        }
        return out.toLongArray();
    }

    private List<SectionEntry> toEntries() {
        List<SectionEntry> entries = new ArrayList<>(sections.size());
        for (Long2ObjectMap.Entry<BitSet> entry : sections.long2ObjectEntrySet()) {
            entries.add(new SectionEntry(entry.getLongKey(), entry.getValue().toLongArray()));
        }
        return entries;
    }

    private static SectionBitStore fromEntries(List<SectionEntry> entries) {
        SectionBitStore store = new SectionBitStore();
        for (SectionEntry entry : entries) {
            // Guard against corrupt/hand-edited saves: a longer array would set bits at index
            // >= 4096 that alias onto other positions on read-back and drive unbounded allocation.
            if (entry.words().length > WORDS_PER_SECTION) {
                continue;
            }
            BitSet bits = BitSet.valueOf(entry.words());
            if (!bits.isEmpty()) {
                store.sections.put(entry.section(), bits);
            }
        }
        return store;
    }

    private record SectionEntry(long section, long[] words) {
        private static final Codec<SectionEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("section").forGetter(SectionEntry::section),
                Codec.LONG_STREAM.fieldOf("words")
                        .xmap(LongStream::toArray, LongStream::of)
                        .forGetter(SectionEntry::words)
        ).apply(instance, SectionEntry::new));
    }

    /**
     * Serializes to a list of section entries. On load, transparently accepts the legacy format
     * (a flat long array of absolute block positions) and upgrades it in place, so existing worlds
     * keep their tracked blocks.
     */
    public static final Codec<SectionBitStore> CODEC = Codec.either(
            Codec.list(SectionEntry.CODEC),
            Codec.LONG_STREAM
    ).xmap(
            either -> either.map(SectionBitStore::fromEntries, SectionBitStore::fromLegacy),
            store -> Either.left(store.toEntries())
    );

    private static SectionBitStore fromLegacy(LongStream legacy) {
        SectionBitStore store = new SectionBitStore();
        legacy.forEach(store::add);
        return store;
    }
}
