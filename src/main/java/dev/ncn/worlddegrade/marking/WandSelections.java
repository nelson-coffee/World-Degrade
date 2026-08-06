package dev.ncn.worlddegrade.marking;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WandSelections {
    public record Selection(BlockPos first, @Nullable BlockPos second) {
        public BlockPos min() {
            return second == null ? first : new BlockPos(
                    Math.min(first.getX(), second.getX()),
                    Math.min(first.getY(), second.getY()),
                    Math.min(first.getZ(), second.getZ()));
        }

        public BlockPos max() {
            return second == null ? first : new BlockPos(
                    Math.max(first.getX(), second.getX()),
                    Math.max(first.getY(), second.getY()),
                    Math.max(first.getZ(), second.getZ()));
        }

        public boolean contains(BlockPos pos) {
            BlockPos min = min();
            BlockPos max = max();
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
    }

    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

    @Nullable
    public static Selection get(UUID player) {
        return SELECTIONS.get(player);
    }

    public static void set(UUID player, Selection selection) {
        SELECTIONS.put(player, selection);
    }

    public static void clear(UUID player) {
        SELECTIONS.remove(player);
    }

    private WandSelections() {
    }
}
