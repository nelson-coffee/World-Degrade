package dev.ncn.worlddegrade.degrade;

import dev.ncn.worlddegrade.command.DegradeCommand;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DegradeAreaTest {

    private static long chunk(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    private static long block(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    @Test
    void wholeDimensionAcceptsEverything() {
        DegradeArea area = new DegradeArea.WholeDimension();
        assertTrue(area.containsChunk(chunk(1_000_000, -999)));
        assertTrue(area.containsBlock(block(42, 71, -13)));
    }

    @Test
    void boxSelectsOnlyOverlappingChunks() {
        // Box spans blocks 100..140 on X/Z, i.e. chunks 6..8 (chunk 6 = 96..111, 8 = 128..143).
        DegradeArea area = new DegradeArea.Box(new BoundingBox(100, 60, 100, 140, 90, 140));
        assertTrue(area.containsChunk(chunk(6, 6)));
        assertTrue(area.containsChunk(chunk(8, 8)));
        assertFalse(area.containsChunk(chunk(5, 6)));
        assertFalse(area.containsChunk(chunk(9, 8)));
    }

    @Test
    void boxClipsPerBlock() {
        DegradeArea area = new DegradeArea.Box(new BoundingBox(100, 60, 100, 140, 90, 140));
        assertTrue(area.containsBlock(block(100, 60, 100)));
        assertTrue(area.containsBlock(block(140, 90, 140)));
        // Inside the enclosing chunks but outside the exact box.
        assertFalse(area.containsBlock(block(141, 60, 141)));
        assertFalse(area.containsBlock(block(120, 59, 120)));
        assertFalse(area.containsBlock(block(120, 91, 120)));
    }

    @Test
    void radiusStaysChunkGranular() {
        // Radius 8 around the centre of chunk (0,0). containsChunk clips, containsBlock does not.
        DegradeArea area = new DegradeArea.Radius(8.0, 8.0, 8);
        assertTrue(area.containsChunk(chunk(0, 0)));
        assertFalse(area.containsChunk(chunk(5, 5)));
        // A block far outside the radius but nominally "in an accepted chunk" still passes,
        // preserving the GUI's chunk-granular behaviour.
        assertTrue(area.containsBlock(block(15, 200, 15)));
    }

    @Test
    void chunksMatchTheGivenSetOnly() {
        LongOpenHashSet set = new LongOpenHashSet();
        set.add(chunk(3, 4));
        set.add(chunk(-2, 7));
        DegradeArea area = new DegradeArea.Chunks(set);
        assertTrue(area.containsChunk(chunk(3, 4)));
        assertTrue(area.containsChunk(chunk(-2, 7)));
        assertFalse(area.containsChunk(chunk(3, 5)));
        // Chunk-aligned by definition: every block in an accepted chunk passes.
        assertTrue(area.containsBlock(block(48, 5, 64)));
    }

    // An empty set has no meaningful hull, and the degenerate one it returns is NOT inert:
    // AABB.intersects matches anything straddling x=0/z=0. isEmpty() is what callers must gate on.
    @Test
    void emptyChunksReportsItselfEmptyAndYieldsADegenerateHull() {
        DegradeArea.Chunks area = new DegradeArea.Chunks(new LongOpenHashSet());
        assertTrue(area.isEmpty());
        var bounds = area.scanBounds(-64, 320);
        assertEquals(0.0, bounds.minX);
        assertEquals(0.0, bounds.maxX);
        assertEquals(0.0, bounds.minZ);
        assertEquals(0.0, bounds.maxZ);
        // The trap this guards: a zero-width box still intersects a contraption sitting on origin.
        assertTrue(bounds.intersects(new AABB(-1, 0, -1, 1, 10, 1)));
    }

    @Test
    void nonEmptyAreasAreNotReportedEmpty() {
        LongOpenHashSet set = new LongOpenHashSet();
        set.add(chunk(0, 0));
        assertFalse(new DegradeArea.Chunks(set).isEmpty());
        assertFalse(new DegradeArea.WholeDimension().isEmpty());
        assertFalse(new DegradeArea.Radius(0, 0, 16).isEmpty());
        assertFalse(new DegradeArea.Box(new BoundingBox(0, 0, 0, 1, 1, 1)).isEmpty());
    }

    @Test
    void onlyWholeDimensionReportsItselfAsWholeDimension() {
        assertTrue(new DegradeArea.WholeDimension().isWholeDimension());
        assertFalse(new DegradeArea.Radius(0, 0, 16).isWholeDimension());
        assertFalse(new DegradeArea.Box(new BoundingBox(0, 0, 0, 1, 1, 1)).isWholeDimension());
        assertFalse(new DegradeArea.Chunks(new LongOpenHashSet()).isWholeDimension());
    }

    @Test
    void chunksScanBoundsCoverTheWholeColumnSet() {
        LongOpenHashSet set = new LongOpenHashSet();
        set.add(chunk(0, 0));
        set.add(chunk(2, 1));
        var bounds = new DegradeArea.Chunks(set).scanBounds(-64, 320);
        // chunk 0 starts at block 0; chunk 2 ends at block 47 (2<<4 + 16 = 48 exclusive).
        assertTrue(bounds.minX <= 0 && bounds.maxX >= 48);
        assertTrue(bounds.minZ <= 0 && bounds.maxZ >= 32);
        assertTrue(bounds.minY <= -64 && bounds.maxY >= 320);
    }

    // The server-side entry point (#5/#6) hands DegradeService a Collection<ChunkPos>; ofChunks is
    // the conversion it funnels through, so it must round-trip every column and drop nothing.
    @Test
    void ofChunksWrapsEveryColumn() {
        DegradeArea.Chunks area = DegradeArea.ofChunks(
                List.of(new ChunkPos(3, 4), new ChunkPos(-2, 7)));
        assertEquals(2, area.packedChunks().size());
        assertTrue(area.containsChunk(chunk(3, 4)));
        assertTrue(area.containsChunk(chunk(-2, 7)));
        assertFalse(area.containsChunk(chunk(3, 7)));
    }

    // containsColumn is the narrow phase compat degraders apply to whatever scanBounds' hull returns.
    // Without it a disjoint claim set degrades every contraption in the gap between the claims.
    @Test
    void containsColumnRejectsWhatTheHullWouldHaveSwept() {
        LongOpenHashSet set = new LongOpenHashSet();
        set.add(chunk(0, 0));
        set.add(chunk(50, 50));
        DegradeArea.Chunks area = new DegradeArea.Chunks(set);
        var hull = area.scanBounds(-64, 320);

        // A contraption anchored midway is inside the hull but in no claimed column.
        assertTrue(hull.contains(400.0, 64.0, 400.0));
        assertFalse(area.containsColumn(400.0, 400.0));

        assertTrue(area.containsColumn(0.0, 0.0));
        assertTrue(area.containsColumn(15.9, 15.9));
        assertTrue(area.containsColumn(800.0, 800.0));
        assertFalse(area.containsColumn(16.0, 16.0));
    }

    // Anchors are doubles and can be negative; floor-then-shift must not round toward zero.
    @Test
    void containsColumnFloorsNegativeAnchorsCorrectly() {
        LongOpenHashSet set = new LongOpenHashSet();
        set.add(chunk(-1, -1));
        DegradeArea.Chunks area = new DegradeArea.Chunks(set);
        assertTrue(area.containsColumn(-0.5, -0.5));
        assertTrue(area.containsColumn(-16.0, -16.0));
        assertFalse(area.containsColumn(0.0, 0.0));
        assertFalse(area.containsColumn(-16.1, -16.1));
    }

    @Test
    void containsColumnFollowsTheBoxChunkSelection() {
        DegradeArea area = new DegradeArea.Box(new BoundingBox(100, 60, 100, 140, 90, 140));
        assertTrue(area.containsColumn(100.0, 100.0));
        // Chunk-granular on purpose: a contraption anchored in chunk 8 but bodily outside the exact
        // box is still selected, because a contraption is degraded as a whole or not at all.
        assertTrue(area.containsColumn(143.0, 143.0));
        assertFalse(area.containsColumn(144.0, 144.0));
    }

    @Test
    void wholeDimensionContainsEveryColumn() {
        assertTrue(new DegradeArea.WholeDimension().containsColumn(-9_999_999.0, 9_999_999.0));
    }

    // Pins the inclusive/exclusive edge: chunk 6 spans blocks 96..111, so a box ending exactly at
    // 111 must still select it, and one starting at 112 must not.
    @Test
    void boxChunkSelectionIsInclusiveAtTheChunkBoundary() {
        DegradeArea endsOnBoundary = new DegradeArea.Box(new BoundingBox(96, 0, 96, 111, 10, 111));
        assertTrue(endsOnBoundary.containsChunk(chunk(6, 6)));
        assertFalse(endsOnBoundary.containsChunk(chunk(7, 7)));

        DegradeArea startsNextChunk = new DegradeArea.Box(new BoundingBox(112, 0, 112, 120, 10, 120));
        assertFalse(startsNextChunk.containsChunk(chunk(6, 6)));
        assertTrue(startsNextChunk.containsChunk(chunk(7, 7)));
    }

    // /degrade chunks rejects on `count > MAX_AREA_CHUNKS`, so a rectangle of exactly the cap is
    // allowed and one column more is not. Pins the > vs >= rather than leaving it to a reading.
    @Test
    void chunkRectangleCountPinsTheCommandLimit() {
        assertEquals(10_000L, DegradeArea.chunkRectangleCount(0, 0, 99, 99));
        assertEquals(DegradeCommand.MAX_AREA_CHUNKS, DegradeArea.chunkRectangleCount(0, 0, 99, 99));
        assertTrue(DegradeArea.chunkRectangleCount(0, 0, 99, 99) <= DegradeCommand.MAX_AREA_CHUNKS);
        assertTrue(DegradeArea.chunkRectangleCount(0, 0, 99, 100) > DegradeCommand.MAX_AREA_CHUNKS);
    }

    // The /degrade chunks guard multiplies the rectangle's edges as longs before allocating, so a
    // wide typo is rejected instead of overflowing an int or exhausting memory.
    @Test
    void chunkRectangleCountIsInclusiveAndOrderIndependent() {
        assertEquals(9L, DegradeArea.chunkRectangleCount(3, 4, 5, 6));
        // Reversed corners give the same count.
        assertEquals(9L, DegradeArea.chunkRectangleCount(5, 6, 3, 4));
        assertEquals(1L, DegradeArea.chunkRectangleCount(0, 0, 0, 0));
        // Full int span would overflow a naive int multiply; the long path stays positive.
        assertTrue(DegradeArea.chunkRectangleCount(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0) > 0);
    }

    // BoundingBox throws on inverted bounds in a dev environment and silently swaps min/max in
    // production, so a Y band entirely outside the build height must be rejected before construction.
    @Test
    void clampedBoxRejectsSelectionsOutsideBuildHeight() {
        assertNull(DegradeArea.clampedBox(
                new BlockPos(0, 400, 0), new BlockPos(20, 500, 20), -64, 320));
        assertNull(DegradeArea.clampedBox(
                new BlockPos(0, -200, 0), new BlockPos(20, -100, 20), -64, 320));
    }

    @Test
    void clampedBoxClampsToBuildHeightAndNormalisesCorners() {
        DegradeArea.Box box = DegradeArea.clampedBox(
                new BlockPos(20, 500, 30), new BlockPos(0, -200, 10), -64, 320);
        assertNotNull(box);
        assertEquals(0, box.box().minX());
        assertEquals(20, box.box().maxX());
        assertEquals(10, box.box().minZ());
        assertEquals(30, box.box().maxZ());
        assertEquals(-64, box.box().minY());
        assertEquals(319, box.box().maxY());
    }

    // A single-layer selection at the very top of the world is legal, not an inverted box.
    @Test
    void clampedBoxKeepsASingleLayerAtTheBuildCeiling() {
        DegradeArea.Box box = DegradeArea.clampedBox(
                new BlockPos(0, 319, 0), new BlockPos(0, 400, 0), -64, 320);
        assertNotNull(box);
        assertEquals(319, box.box().minY());
        assertEquals(319, box.box().maxY());
    }

    @Test
    void chunkRectangleFillsInclusiveCornersRegardlessOfOrder() {
        DegradeArea.Chunks area = DegradeArea.chunkRectangle(5, 6, 3, 4);
        assertEquals(9, area.packedChunks().size());
        assertTrue(area.containsChunk(chunk(3, 4)));
        assertTrue(area.containsChunk(chunk(5, 6)));
        assertTrue(area.containsChunk(chunk(4, 5)));
        assertFalse(area.containsChunk(chunk(2, 4)));
        assertFalse(area.containsChunk(chunk(6, 6)));
    }
}
