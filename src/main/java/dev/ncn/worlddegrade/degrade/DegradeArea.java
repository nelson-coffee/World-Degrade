package dev.ncn.worlddegrade.degrade;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Describes <em>where</em> a degradation run applies. Every entry point (GUI radius, the
 * {@code /degrade area} command, OPAC claim expiry) expresses its target as one of these
 * shapes, so the pipeline no longer needs a {@link net.minecraft.server.level.ServerPlayer}.
 *
 * <p>It answers two distinct questions that must not be conflated:
 * <ul>
 *   <li>{@link #containsChunk(long)} — which tracked chunks get enqueued.</li>
 *   <li>{@link #containsBlock(long)} — which positions inside an enqueued chunk get degraded.</li>
 * </ul>
 * The GUI radius path is chunk-granular today (a chunk within the radius degrades entirely),
 * so {@link Radius} and {@link WholeDimension} deliberately return {@code true} for every block.
 * Only {@link Box} clips per block, because an admin typing exact coordinates expects them.
 */
public sealed interface DegradeArea
        permits DegradeArea.WholeDimension, DegradeArea.Radius, DegradeArea.Box, DegradeArea.Chunks {

    boolean containsChunk(long packedChunk);

    boolean containsBlock(long packedPos);

    /**
     * A coarse <em>hull</em> for compat entity scans (Create contraptions, Sable ships). It is only
     * a cheap broad phase: {@link Chunks} returns the bounding rectangle of a possibly disjoint set,
     * so the hull can cover thousands of columns the area does not select. Callers must re-filter
     * whatever the scan returns with {@link #containsColumn} — see {@link #containsColumn} for why
     * the narrow phase is deliberately chunk-granular.
     */
    AABB scanBounds(int minBuildHeight, int maxBuildHeight);

    /**
     * Whether the chunk column holding these block coordinates is selected. This is the narrow phase
     * compat degraders apply to the entities {@link #scanBounds} hands them.
     *
     * <p>It is chunk-granular on purpose, and deliberately coarser than {@link #containsBlock} for
     * {@link Box}: a contraption or ship is an atomic unit — it is disassembled as a whole before
     * its blocks exist to be clipped — so the choice is to degrade all of it or none of it. Anchored
     * in a selected column means all of it, which matches the chunk-first rule the vanilla path uses
     * to pick chunks. A {@code /degrade area} box that clips a large contraption therefore still
     * degrades the whole contraption, not just the part inside the box.
     */
    default boolean containsColumn(double blockX, double blockZ) {
        return containsChunk(ChunkPos.asLong(Mth.floor(blockX) >> 4, Mth.floor(blockZ) >> 4));
    }

    /** True when nothing can ever be selected, so a run would be pure wasted scanning. */
    default boolean isEmpty() {
        return false;
    }

    default boolean isWholeDimension() {
        return this instanceof WholeDimension;
    }

    /**
     * Wraps a collection of chunk columns as a {@link Chunks} area. This is the conversion the
     * server-side entry points (#5 schedule, #6 OPAC) feed their claims through, so it is kept
     * here as a pure, testable function rather than inlined in {@link DegradeService}.
     */
    static Chunks ofChunks(Collection<ChunkPos> chunks) {
        LongOpenHashSet packed = new LongOpenHashSet(chunks.size());
        for (ChunkPos chunk : chunks) {
            packed.add(chunk.toLong());
        }
        return new Chunks(packed);
    }

    /**
     * Number of chunk columns an inclusive rectangle covers, with the corners normalised. The
     * {@code /degrade chunks} command checks this <em>before</em> allocating the set so a typo
     * spanning billions of columns is rejected instead of exhausting memory. Uses {@code long}
     * arithmetic so the multiply cannot overflow for any {@code int} inputs.
     */
    static long chunkRectangleCount(int fromX, int fromZ, int toX, int toZ) {
        long width = Math.abs((long) toX - fromX) + 1;
        long depth = Math.abs((long) toZ - fromZ) + 1;
        return width * depth;
    }

    /**
     * A {@link Box} from two arbitrary corners, with Y clamped to the dimension's build height, or
     * {@code null} when the selection lies entirely outside it. {@link BoundingBox} reacts badly to
     * inverted bounds — it throws in a dev environment and silently swaps min/max in production —
     * so the caller must reject the empty case rather than construct one.
     *
     * @param maxBuildHeight exclusive, as returned by {@code Level#getMaxBuildHeight()}.
     */
    @Nullable
    static Box clampedBox(BlockPos from, BlockPos to, int minBuildHeight, int maxBuildHeight) {
        int minY = Math.max(minBuildHeight, Math.min(from.getY(), to.getY()));
        int maxY = Math.min(maxBuildHeight - 1, Math.max(from.getY(), to.getY()));
        if (minY > maxY) {
            return null;
        }
        return new Box(new BoundingBox(
                Math.min(from.getX(), to.getX()), minY, Math.min(from.getZ(), to.getZ()),
                Math.max(from.getX(), to.getX()), maxY, Math.max(from.getZ(), to.getZ())));
    }

    /** Every chunk column inside the inclusive rectangle, corners normalised. */
    static Chunks chunkRectangle(int fromX, int fromZ, int toX, int toZ) {
        int minX = Math.min(fromX, toX);
        int maxX = Math.max(fromX, toX);
        int minZ = Math.min(fromZ, toZ);
        int maxZ = Math.max(fromZ, toZ);
        LongOpenHashSet packed = new LongOpenHashSet();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                packed.add(ChunkPos.asLong(cx, cz));
            }
        }
        return new Chunks(packed);
    }

    /** Entire dimension — the GUI's "whole world" mode. */
    record WholeDimension() implements DegradeArea {
        @Override
        public boolean containsChunk(long packedChunk) {
            return true;
        }

        @Override
        public boolean containsBlock(long packedPos) {
            return true;
        }

        @Override
        public AABB scanBounds(int minBuildHeight, int maxBuildHeight) {
            return new AABB(-30_000_000, minBuildHeight, -30_000_000,
                    30_000_000, maxBuildHeight, 30_000_000);
        }
    }

    /** A circle of {@code radius} blocks around ({@code x}, {@code z}) — the GUI slider. */
    record Radius(double x, double z, int radius) implements DegradeArea {
        @Override
        public boolean containsChunk(long packedChunk) {
            int minX = ChunkPos.getX(packedChunk) << 4;
            int minZ = ChunkPos.getZ(packedChunk) << 4;
            double nearestX = Math.max(minX, Math.min(x, minX + 15));
            double nearestZ = Math.max(minZ, Math.min(z, minZ + 15));
            double dx = x - nearestX;
            double dz = z - nearestZ;
            return dx * dx + dz * dz <= (double) radius * radius;
        }

        @Override
        public boolean containsBlock(long packedPos) {
            return true;
        }

        @Override
        public AABB scanBounds(int minBuildHeight, int maxBuildHeight) {
            return new AABB(x - radius, minBuildHeight, z - radius,
                    x + radius, maxBuildHeight, z + radius);
        }
    }

    /** An exact block box — the {@code /degrade area} command. Clips per block. */
    record Box(BoundingBox box) implements DegradeArea {
        @Override
        public boolean containsChunk(long packedChunk) {
            int chunkX = ChunkPos.getX(packedChunk);
            int chunkZ = ChunkPos.getZ(packedChunk);
            int minBlockX = chunkX << 4;
            int minBlockZ = chunkZ << 4;
            return box.intersects(minBlockX, minBlockZ, minBlockX + 15, minBlockZ + 15);
        }

        @Override
        public boolean containsBlock(long packedPos) {
            int px = BlockPos.getX(packedPos);
            int py = BlockPos.getY(packedPos);
            int pz = BlockPos.getZ(packedPos);
            return px >= box.minX() && px <= box.maxX()
                    && py >= box.minY() && py <= box.maxY()
                    && pz >= box.minZ() && pz <= box.maxZ();
        }

        @Override
        public AABB scanBounds(int minBuildHeight, int maxBuildHeight) {
            return new AABB(box.minX(), minBuildHeight, box.minZ(),
                    box.maxX() + 1, maxBuildHeight, box.maxZ() + 1);
        }
    }

    /** A set of chunk columns (packed via {@link ChunkPos#toLong}) — OPAC claims. */
    record Chunks(LongOpenHashSet packedChunks) implements DegradeArea {
        @Override
        public boolean containsChunk(long packedChunk) {
            return packedChunks.contains(packedChunk);
        }

        @Override
        public boolean containsBlock(long packedPos) {
            return true;
        }

        @Override
        public boolean isEmpty() {
            return packedChunks.isEmpty();
        }

        @Override
        public AABB scanBounds(int minBuildHeight, int maxBuildHeight) {
            // Degenerate rather than empty: AABB.intersects treats a zero-width box at the origin as
            // overlapping anything straddling x=0/z=0. Callers must reject an empty area outright
            // instead of relying on this hull to select nothing.
            if (packedChunks.isEmpty()) {
                return new AABB(0, minBuildHeight, 0, 0, maxBuildHeight, 0);
            }
            int minChunkX = Integer.MAX_VALUE;
            int minChunkZ = Integer.MAX_VALUE;
            int maxChunkX = Integer.MIN_VALUE;
            int maxChunkZ = Integer.MIN_VALUE;
            for (long packed : packedChunks) {
                int cx = ChunkPos.getX(packed);
                int cz = ChunkPos.getZ(packed);
                minChunkX = Math.min(minChunkX, cx);
                minChunkZ = Math.min(minChunkZ, cz);
                maxChunkX = Math.max(maxChunkX, cx);
                maxChunkZ = Math.max(maxChunkZ, cz);
            }
            return new AABB(minChunkX << 4, minBuildHeight, minChunkZ << 4,
                    (maxChunkX << 4) + 16, maxBuildHeight, (maxChunkZ << 4) + 16);
        }
    }
}
