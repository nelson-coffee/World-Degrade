package dev.ncn.worlddegrade.data;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The layered wear-chain lookup that backs {@code BrickWeatherEffect}.
 *
 * <p>Two maps are kept side by side: {@code builtin} holds the hardcoded defaults plus any compat
 * mod steps (registered once at setup and never cleared), while {@code datapack} holds chains loaded
 * from a datapack (rebuilt on every resource reload). Lookups consult the datapack map first, so a
 * datapack chain overrides the built-in default for exactly the blocks it names and nothing else —
 * the per-block override behaviour the issue asks for falls straight out of "check datapack, then
 * built-in".
 */
public final class WearChains {
    private static final Map<Block, Block> BUILTIN = new HashMap<>();
    private static final Map<Block, Block> DATAPACK = new HashMap<>();
    private static final Set<Block> BUILTIN_KNOWN = new HashSet<>();
    private static final Set<Block> DATAPACK_KNOWN = new HashSet<>();
    private static final Map<Block, Float> RATE_SCALE = new HashMap<>();
    private static final Map<Block, Integer> STEP_BONUS = new HashMap<>();

    private WearChains() {
    }

    public static void registerBuiltin(Block from, Block to) {
        BUILTIN.put(from, to);
        BUILTIN_KNOWN.add(from);
        BUILTIN_KNOWN.add(to);
    }

    public static void registerBuiltin(Block from, Block to, float rateScale, int stepBonus) {
        registerBuiltin(from, to);
        if (rateScale != 1.0f) {
            RATE_SCALE.put(from, rateScale);
        }
        if (stepBonus > 0) {
            STEP_BONUS.put(from, stepBonus);
        }
    }

    /** Wipes the datapack layer ahead of a reload; built-in defaults are untouched. */
    public static void clearDatapack() {
        DATAPACK.clear();
        DATAPACK_KNOWN.clear();
    }

    public static void putDatapack(Block from, Block to) {
        DATAPACK.put(from, to);
        DATAPACK_KNOWN.add(from);
        DATAPACK_KNOWN.add(to);
    }

    /** A snapshot of the built-in defaults, used by {@code /worlddegrade generate} for reference output. */
    public static Map<Block, Block> builtinEntries() {
        return new HashMap<>(BUILTIN);
    }

    @Nullable
    public static Block wearTarget(Block from) {
        Block datapack = DATAPACK.get(from);
        return datapack != null ? datapack : BUILTIN.get(from);
    }

    public static boolean isKnown(Block block) {
        return DATAPACK_KNOWN.contains(block) || BUILTIN_KNOWN.contains(block);
    }

    public static float rateScale(Block block) {
        return RATE_SCALE.getOrDefault(block, 1.0f);
    }

    public static int stepBonus(Block block) {
        return STEP_BONUS.getOrDefault(block, 0);
    }

    @Nullable
    public static BlockState weather(BlockState state) {
        Block worn = wearTarget(state.getBlock());
        if (worn != null) {
            return worn.withPropertiesOf(state);
        }
        return WeatheringCopper.getNext(state.getBlock())
                .map(next -> next.withPropertiesOf(state))
                .orElse(null);
    }
}
