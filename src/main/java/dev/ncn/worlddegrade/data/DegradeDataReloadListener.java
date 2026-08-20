package dev.ncn.worlddegrade.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Loads the datapack layer from {@code data/<namespace>/chains/*.json} on every server resource
 * reload (startup and {@code /reload}). Three entry {@code type}s are understood:
 *
 * <ul>
 *   <li>{@code worlddegrade:wear_chain} — a linear {@code chain} of block ids, each linked to the
 *       next.</li>
 *   <li>{@code worlddegrade:chain_entry} — a single {@code from -> into} ramp-in link.</li>
 *   <li>{@code worlddegrade:block_category} — {@code add}/{@code remove} block ids for an effect
 *       category.</li>
 * </ul>
 *
 * <p>The datapack layer is rebuilt from scratch each reload so removed entries actually disappear;
 * built-in defaults live in a separate map and survive untouched.
 */
public final class DegradeDataReloadListener extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    public DegradeDataReloadListener() {
        super(GSON, "chains");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        WearChains.clearDatapack();
        BlockCategories.clearOverrides();

        int chains = 0;
        int ramps = 0;
        int categories = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                LOGGER.warn("World Degrade: datapack chain {} is not a JSON object, skipping", id);
                continue;
            }
            JsonObject json = entry.getValue().getAsJsonObject();
            String type = json.has("type") ? json.get("type").getAsString() : "";
            switch (type) {
                case "worlddegrade:wear_chain" -> {
                    if (applyWearChain(id, json)) {
                        chains++;
                    }
                }
                case "worlddegrade:chain_entry" -> {
                    if (applyChainEntry(id, json)) {
                        ramps++;
                    }
                }
                case "worlddegrade:block_category" -> {
                    if (applyBlockCategory(id, json)) {
                        categories++;
                    }
                }
                default -> LOGGER.warn("World Degrade: datapack chain {} has unknown type '{}', skipping", id, type);
            }
        }
        LOGGER.info("World Degrade: loaded datapack degradation data ({} wear chains, {} chain entries, {} category overrides)",
                chains, ramps, categories);
    }

    private boolean applyWearChain(ResourceLocation id, JsonObject json) {
        if (!json.has("chain") || !json.get("chain").isJsonArray()) {
            LOGGER.warn("World Degrade: wear_chain {} is missing a 'chain' array, skipping", id);
            return false;
        }
        JsonArray chain = json.getAsJsonArray("chain");
        Block previous = null;
        boolean linked = false;
        for (JsonElement element : chain) {
            Block block = block(element.getAsString());
            if (block == null) {
                previous = null;
                continue;
            }
            if (previous != null && previous != block) {
                WearChains.putDatapack(previous, block);
                linked = true;
            }
            previous = block;
        }
        return linked;
    }

    private boolean applyChainEntry(ResourceLocation id, JsonObject json) {
        Block from = json.has("from") ? block(json.get("from").getAsString()) : null;
        Block into = json.has("into") ? block(json.get("into").getAsString()) : null;
        if (from == null || into == null || from == into) {
            LOGGER.warn("World Degrade: chain_entry {} did not resolve to two distinct blocks, skipping", id);
            return false;
        }
        WearChains.putDatapack(from, into);
        return true;
    }

    private boolean applyBlockCategory(ResourceLocation id, JsonObject json) {
        String categoryName = json.has("category") ? json.get("category").getAsString() : "";
        BlockCategories.Category category = BlockCategories.Category.byName(categoryName);
        if (category == null) {
            LOGGER.warn("World Degrade: block_category {} names unknown category '{}', skipping", id, categoryName);
            return false;
        }
        forEach(json, "add", block -> BlockCategories.add(category, block));
        forEach(json, "remove", block -> BlockCategories.remove(category, block));
        return true;
    }

    private void forEach(JsonObject json, String key, java.util.function.Consumer<Block> action) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return;
        }
        for (JsonElement element : json.getAsJsonArray(key)) {
            Block block = block(element.getAsString());
            if (block != null) {
                action.accept(block);
            }
        }
    }

    @Nullable
    private Block block(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            LOGGER.warn("World Degrade: '{}' is not a valid block id", rawId);
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR && !id.equals(BuiltInRegistries.BLOCK.getKey(Blocks.AIR))) {
            // Absent block (mod not installed): quietly skip so a shared datapack stays portable.
            return null;
        }
        return block;
    }
}
