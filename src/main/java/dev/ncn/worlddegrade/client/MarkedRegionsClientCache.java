package dev.ncn.worlddegrade.client;

import dev.ncn.worlddegrade.marking.MarkedRegions;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MarkedRegionsClientCache {
    private static List<MarkedRegions.Region> regions = List.of();
    @Nullable
    private static BlockPos selectionFirst;
    @Nullable
    private static BlockPos selectionSecond;

    public static List<MarkedRegions.Region> regions() {
        return regions;
    }

    public static void setRegions(List<MarkedRegions.Region> newRegions) {
        regions = List.copyOf(newRegions);
    }

    @Nullable
    public static BlockPos selectionFirst() {
        return selectionFirst;
    }

    @Nullable
    public static BlockPos selectionSecond() {
        return selectionSecond;
    }

    public static void setSelection(@Nullable BlockPos first, @Nullable BlockPos second) {
        selectionFirst = first;
        selectionSecond = second;
    }

    private MarkedRegionsClientCache() {
    }
}
