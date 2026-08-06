package dev.ncn.worlddegrade.compat.chipped;

import com.mojang.logging.LogUtils;
import dev.ncn.worlddegrade.degrade.effects.BrickWeatherEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ChippedWearTable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CHIPPED = "chipped";

    private static final String[] LADDER = {"cracked", "weathered", "eroded", "rough", "cobbled"};

    private static final Map<String, String> BASE_OVERRIDES = Map.of(
            "borderless_bricks", "bricks",
            "special_lantern", "lantern",
            "special_soul_lantern", "soul_lantern",
            "waxed_exposed_copper_block", "waxed_exposed_copper");

    private static final float RATE_BOOST = 2.0f;
    private static final int STEP_BONUS = 8;

    private ChippedWearTable() {
    }

    static void register(java.util.function.BiConsumer<Block, Block> sink) {
        int entries = 0;
        int families = 0;
        int longest = 0;
        List<String> unresolved = new ArrayList<>();

        for (Map.Entry<String, List<Block>> family : collectFamilies().entrySet()) {
            Block base = baseFor(family.getKey());
            if (base == null) {
                unresolved.add(family.getKey());
                continue;
            }
            List<Block> variants = family.getValue();
            List<Block> ladder = buildLadder(variants, base);
            for (int i = 0; i < ladder.size(); i++) {
                Block next = i + 1 < ladder.size() ? ladder.get(i + 1) : base;
                entries += link(sink, ladder.get(i), next);
            }
            List<Block> topTier = topTier(variants);
            for (Block variant : variants) {
                if (ladder.contains(variant)) {
                    continue;
                }
                Block target = topTier.isEmpty()
                        ? base
                        : topTier.get(Math.floorMod(idOf(variant).hashCode(), topTier.size()));
                entries += link(sink, variant, target);
            }
            families++;
            longest = Math.max(longest, ladder.size() + 1);
        }

        if (unresolved.isEmpty()) {
            LOGGER.info("World Degrade: registered {} Chipped wear steps across {} families "
                    + "(longest ladder {})", entries, families, longest);
        } else {
            LOGGER.warn("World Degrade: registered {} Chipped wear steps across {} families, but {} "
                            + "tag(s) had no vanilla base — Chipped's naming may have changed: {}",
                    entries, families, unresolved.size(), unresolved);
        }
    }

    private static int link(java.util.function.BiConsumer<Block, Block> sink, Block from, Block to) {
        if (from == null || to == null || from == to) {
            return 0;
        }
        BrickWeatherEffect.addWear(from, to, RATE_BOOST, STEP_BONUS);
        return 1;
    }

    private static Map<String, List<Block>> collectFamilies() {
        Map<String, List<Block>> families = new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = idOf(block);
            if (!id.getNamespace().equals(CHIPPED)) {
                continue;
            }
            String family = familyOf(block);
            if (family != null) {
                families.computeIfAbsent(family, key -> new ArrayList<>()).add(block);
            }
        }
        return families;
    }

    @Nullable
    private static String familyOf(Block block) {
        for (TagKey<Block> tag : block.defaultBlockState().getTags().toList()) {
            if (tag.location().getNamespace().equals(CHIPPED)) {
                return tag.location().getPath();
            }
        }
        return null;
    }

    private static List<Block> buildLadder(List<Block> variants, Block base) {
        List<Block> ladder = new ArrayList<>();
        for (String word : LADDER) {
            List<Block> tier = tier(variants, word);
            if (!tier.isEmpty() && tier.get(0) != base) {
                ladder.add(tier.get(0));
            }
        }
        return ladder;
    }

    private static List<Block> topTier(List<Block> variants) {
        for (String word : LADDER) {
            List<Block> tier = tier(variants, word);
            if (!tier.isEmpty()) {
                return tier;
            }
        }
        return List.of();
    }

    private static List<Block> tier(List<Block> variants, String word) {
        List<Block> tier = new ArrayList<>();
        for (Block block : variants) {
            if (idOf(block).getPath().startsWith(word + "_")) {
                tier.add(block);
            }
        }
        tier.sort((a, b) -> idOf(a).getPath().compareTo(idOf(b).getPath()));
        return tier;
    }

    @Nullable
    private static Block baseFor(String family) {
        String path = BASE_OVERRIDES.getOrDefault(family, family);
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(path));
        return block == Blocks.AIR ? null : block;
    }

    private static ResourceLocation idOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }
}
