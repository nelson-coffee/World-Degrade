package dev.ncn.worlddegrade.compat.rechiseled;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RechiseledWearTable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RECIPE_FOLDER = "chiseling_recipes";
    private static final String RECIPE_TYPE = "rechiseled:chiseling";

    private static final Set<Block> MASKING_RISK = Set.of(
            Blocks.GLOWSTONE, Blocks.BONE_BLOCK, Blocks.AMETHYST_BLOCK, Blocks.BLUE_ICE,
            Blocks.COAL_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.GOLD_BLOCK,
            Blocks.IRON_BLOCK, Blocks.LAPIS_BLOCK, Blocks.NETHERITE_BLOCK, Blocks.REDSTONE_BLOCK,
            Blocks.RED_NETHER_BRICKS);

    private static final Map<Block, Block> MASKED_VARIANTS = new HashMap<>();

    private static final float RATE_BOOST = 2.0f;
    private static final int STEP_BONUS = 8;

    private RechiseledWearTable() {
    }

    static Map<Block, Block> maskedVariants() {
        return MASKED_VARIANTS;
    }

    private record ParsedEntry(@Nullable ResourceLocation blockId, @Nullable Block block,
                                @Nullable Block slab, @Nullable Block stairs,
                                @Nullable Block connectingBlock, @Nullable Block connectingSlab,
                                @Nullable Block connectingStairs) {
    }

    private static final class Counters {
        final Set<Block> sources = new HashSet<>();
        int registered;
        int skippedMasking;
        int skippedNoShape;
        int skippedOwned;
        int unresolved;
        int unreadable;
    }

    private static void link(Block from, Block to, Counters counters) {
        if (from == to) {
            return;
        }
        if (dev.ncn.worlddegrade.degrade.effects.BrickWeatherEffect.wearTarget(from) != null) {
            counters.skippedOwned++;
            return;
        }
        dev.ncn.worlddegrade.degrade.effects.BrickWeatherEffect.addWear(from, to, RATE_BOOST, STEP_BONUS);
        counters.sources.add(from);
        counters.registered++;
    }

    static void register(MinecraftServer server) {
        // listResourceStacks, not listResources: some addons (e.g. Rechiseled: Create) ship a file
        // at the SAME path under the base mod's own namespace, fully replacing rather than adding to
        // it (data/rechiseled/chiseling_recipes/andesite.json exists in both the base mod's jar and
        // the Create addon's jar). Reading only the highest-priority layer would silently drop
        // whichever one loses, so every layer at each location is processed independently instead.
        Map<ResourceLocation, List<Resource>> files = server.getResourceManager()
                .listResourceStacks(RECIPE_FOLDER, location -> location.getPath().endsWith(".json"));

        Set<String> rechiseledNamespaces = new HashSet<>();
        for (ResourceLocation location : files.keySet()) {
            rechiseledNamespaces.add(location.getNamespace());
        }

        Counters counters = new Counters();
        for (Map.Entry<ResourceLocation, List<Resource>> fileEntry : files.entrySet()) {
            for (Resource resource : fileEntry.getValue()) {
                registerFile(fileEntry.getKey(), resource, rechiseledNamespaces, counters);
            }
        }

        LOGGER.info("World Degrade: registered {} Rechiseled wear steps "
                        + "({} skipped: masking-risk material, {} skipped: no vanilla shape to wear into, "
                        + "{} skipped: already owned by another compat, "
                        + "{} unresolved id(s), {} file(s) unreadable)",
                counters.registered, counters.skippedMasking, counters.skippedNoShape,
                counters.skippedOwned, counters.unresolved, counters.unreadable);
        reportChains(counters.sources);
    }

    private static void reportChains(Set<Block> sources) {
        Map<Integer, Integer> depths = new HashMap<>();
        List<Block> looping = new ArrayList<>();
        int longest = 0;
        for (Block start : sources) {
            Set<Block> seen = new HashSet<>();
            Block current = start;
            int length = 0;
            boolean cycle = false;
            while (true) {
                if (!seen.add(current)) {
                    cycle = true;
                    break;
                }
                Block next = dev.ncn.worlddegrade.degrade.effects.BrickWeatherEffect.wearTarget(current);
                if (next == null) {
                    break;
                }
                length++;
                current = next;
            }
            if (cycle) {
                looping.add(start);
                continue;
            }
            longest = Math.max(longest, length);
            depths.merge(length, 1, Integer::sum);
        }
        StringBuilder spread = new StringBuilder();
        for (int depth = 1; depth <= longest; depth++) {
            int count = depths.getOrDefault(depth, 0);
            if (count > 0) {
                spread.append(spread.isEmpty() ? "" : ", ").append(count).append(" need ").append(depth);
            }
        }
        LOGGER.info("World Degrade: Rechiseled chains to fully-worn — longest {} step(s); {}",
                longest, spread.isEmpty() ? "none" : spread);
        for (Block start : looping) {
            LOGGER.error("World Degrade: Rechiseled wear chain from {} loops, so those blocks can "
                            + "never wear out and will never collapse",
                    BuiltInRegistries.BLOCK.getKey(start));
        }
    }

    private static void registerFile(ResourceLocation key, Resource resource, Set<String> rechiseledNamespaces,
                                      Counters counters) {
        JsonObject json;
        try (BufferedReader reader = resource.openAsReader()) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("World Degrade: could not read Rechiseled recipe {}", key, e);
            counters.unreadable++;
            return;
        }
        if (!RECIPE_TYPE.equals(getString(json, "type"))) {
            return;
        }
        JsonArray entries = json.getAsJsonArray("entries");
        if (entries == null) {
            counters.unreadable++;
            return;
        }

        // A single file can bundle several independent chains (e.g. andesite.json covers both
        // "andesite" and "polished_andesite"), so the base is whichever non-Rechiseled entry most
        // recently appeared, not just the first one in the file.
        ParsedEntry base = null;
        boolean masked = false;
        for (JsonElement element : entries) {
            ParsedEntry parsed = parseEntry(element);
            if (parsed.blockId() == null) {
                continue;
            }
            if (!rechiseledNamespaces.contains(parsed.blockId().getNamespace())) {
                base = parsed.block() != null ? parsed : null;
                masked = base != null && MASKING_RISK.contains(base.block());
                // A base entry can carry its own connecting-texture companion
                // (e.g. "block": "create:polished_cut_andesite", "connecting_block": "rechiseledcreate:...").
                if (base != null) {
                    registerAgainstBase(null, null, null,
                            parsed.connectingBlock(), parsed.connectingSlab(), parsed.connectingStairs(),
                            base, masked, counters);
                }
                continue;
            }
            if (base == null) {
                counters.unresolved++;
                continue;
            }
            if (parsed.block() == null) {
                counters.unresolved++;
            }
            registerAgainstBase(parsed.block(), parsed.slab(), parsed.stairs(),
                    parsed.connectingBlock(), parsed.connectingSlab(), parsed.connectingStairs(),
                    base, masked, counters);
        }
    }

    private static void registerAgainstBase(@Nullable Block block, @Nullable Block slab, @Nullable Block stairs,
                                              @Nullable Block connectingBlock, @Nullable Block connectingSlab,
                                              @Nullable Block connectingStairs, ParsedEntry base, boolean masked,
                                              Counters counters) {
        if (masked) {
            for (Block b : new Block[]{block, slab, stairs, connectingBlock, connectingSlab, connectingStairs}) {
                if (b != null) {
                    MASKED_VARIANTS.put(b, base.block());
                    counters.skippedMasking++;
                }
            }
            return;
        }
        for (Block b : new Block[]{block, connectingBlock}) {
            if (b != null) {
                link(b, base.block(), counters);
            }
        }
        for (Block b : new Block[]{slab, connectingSlab}) {
            if (b == null) {
                continue;
            }
            if (base.slab() != null) {
                link(b, base.slab(), counters);
            } else {
                counters.skippedNoShape++;
            }
        }
        for (Block b : new Block[]{stairs, connectingStairs}) {
            if (b == null) {
                continue;
            }
            if (base.stairs() != null) {
                link(b, base.stairs(), counters);
            } else {
                counters.skippedNoShape++;
            }
        }
    }

    private static ParsedEntry parseEntry(JsonElement element) {
        if (element.isJsonPrimitive()) {
            ResourceLocation id = ResourceLocation.parse(element.getAsString());
            return new ParsedEntry(id, resolve(id), null, null, null, null, null);
        }
        if (!element.isJsonObject()) {
            return new ParsedEntry(null, null, null, null, null, null, null);
        }
        JsonObject obj = element.getAsJsonObject();
        ResourceLocation blockId = idAt(obj, "block", "item", "connecting_block");
        return new ParsedEntry(blockId, resolve(blockId),
                resolve(idAt(obj, "slab")), resolve(idAt(obj, "stairs")),
                resolve(idAt(obj, "connecting_block")), resolve(idAt(obj, "connecting_slab")),
                resolve(idAt(obj, "connecting_stairs")));
    }

    @Nullable
    private static ResourceLocation idAt(JsonObject entry, String... keys) {
        for (String key : keys) {
            if (entry.has(key)) {
                return ResourceLocation.parse(entry.get(key).getAsString());
            }
        }
        return null;
    }

    @Nullable
    private static Block resolve(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block == Blocks.AIR ? null : block;
    }

    @Nullable
    private static String getString(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }
}
