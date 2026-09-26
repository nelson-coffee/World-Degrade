package dev.ncn.worlddegrade.degrade.effects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityLootEffectTest {

    @Test
    void frameOnNorthWallAnchorsToTheBlockBehindIt() {
        BlockPos frame = new BlockPos(10, 64, 20);
        assertEquals(new BlockPos(10, 64, 21),
                EntityLootEffect.frameAnchor(frame, Direction.NORTH));
    }

    @Test
    void frameAnchorFollowsEveryHorizontalFacing() {
        BlockPos frame = new BlockPos(0, 70, 0);
        assertEquals(new BlockPos(0, 70, -1), EntityLootEffect.frameAnchor(frame, Direction.SOUTH));
        assertEquals(new BlockPos(-1, 70, 0), EntityLootEffect.frameAnchor(frame, Direction.EAST));
        assertEquals(new BlockPos(1, 70, 0), EntityLootEffect.frameAnchor(frame, Direction.WEST));
    }

    @Test
    void frameOnFloorOrCeilingAnchorsVertically() {
        BlockPos frame = new BlockPos(5, 64, 5);
        assertEquals(new BlockPos(5, 63, 5), EntityLootEffect.frameAnchor(frame, Direction.UP));
        assertEquals(new BlockPos(5, 65, 5), EntityLootEffect.frameAnchor(frame, Direction.DOWN));
    }

    @Test
    void standingEntityAnchorsToTheBlockBelow() {
        assertEquals(new BlockPos(-3, 99, 7),
                EntityLootEffect.standingAnchor(new BlockPos(-3, 100, 7)));
    }

    @Test
    void anchorIsNeverTheEntitysOwnBlock() {
        BlockPos frame = new BlockPos(2, 2, 2);
        for (Direction facing : Direction.values()) {
            assertEquals(false, EntityLootEffect.frameAnchor(frame, facing).equals(frame),
                    "anchor must resolve to a neighbouring block for facing " + facing);
        }
    }
}
