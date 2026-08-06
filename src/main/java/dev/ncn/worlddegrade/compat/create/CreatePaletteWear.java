package dev.ncn.worlddegrade.compat.create;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

final class CreatePaletteWear {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CREATE = "create";

    private static final String[] NATIVE_FAMILIES = {
            "asurine", "crimsite", "limestone", "ochrum", "scorchia", "scoria", "veridium"};

    private static final Map<String, Block> VANILLA_FAMILIES = new HashMap<>();

    private static final Map<String, Block[]> VANILLA_SHAPES = new HashMap<>();

    static {
        VANILLA_FAMILIES.put("andesite", Blocks.ANDESITE);
        VANILLA_FAMILIES.put("diorite", Blocks.DIORITE);
        VANILLA_FAMILIES.put("granite", Blocks.GRANITE);
        VANILLA_FAMILIES.put("tuff", Blocks.TUFF);
        VANILLA_FAMILIES.put("calcite", Blocks.CALCITE);
        VANILLA_FAMILIES.put("dripstone", Blocks.DRIPSTONE_BLOCK);
        VANILLA_FAMILIES.put("deepslate", Blocks.DEEPSLATE);

        VANILLA_SHAPES.put("andesite",
                new Block[]{Blocks.ANDESITE_STAIRS, Blocks.ANDESITE_SLAB, Blocks.ANDESITE_WALL});
        VANILLA_SHAPES.put("diorite",
                new Block[]{Blocks.DIORITE_STAIRS, Blocks.DIORITE_SLAB, Blocks.DIORITE_WALL});
        VANILLA_SHAPES.put("granite",
                new Block[]{Blocks.GRANITE_STAIRS, Blocks.GRANITE_SLAB, Blocks.GRANITE_WALL});
        VANILLA_SHAPES.put("tuff",
                new Block[]{Blocks.TUFF_STAIRS, Blocks.TUFF_SLAB, Blocks.TUFF_WALL});
        VANILLA_SHAPES.put("deepslate", new Block[]{Blocks.COBBLED_DEEPSLATE_STAIRS,
                Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.COBBLED_DEEPSLATE_WALL});
    }

    private static final String[] SHAPES = {"_stairs", "_slab", "_wall"};

    private CreatePaletteWear() {
    }

    static void register(BiConsumer<Block, Block> sink) {
        Counter counter = new Counter(sink);

        for (String family : NATIVE_FAMILIES) {
            palette(counter, family, byId(family), null);
        }
        for (Map.Entry<String, Block> entry : VANILLA_FAMILIES.entrySet()) {
            palette(counter, entry.getKey(), entry.getValue(), VANILLA_SHAPES.get(entry.getKey()));
        }

        counter.link("industrial_iron_block", "weathered_iron_block");
        counter.link("industrial_iron_window", "weathered_iron_window");
        counter.link("industrial_iron_window_pane", "weathered_iron_window_pane");

        counter.link("rose_quartz_tiles", "small_rose_quartz_tiles");
        counter.link("small_rose_quartz_tiles", "rose_quartz_block");

        counter.link("bound_cardboard_block", "cardboard_block");

        counter.report();
    }

    private static void palette(Counter counter, String family, @Nullable Block raw,
                                @Nullable Block[] rawShapes) {
        counter.link("small_" + family + "_bricks", "cut_" + family + "_bricks");
        counter.link("cut_" + family + "_bricks", "cut_" + family);
        counter.link("polished_cut_" + family, "cut_" + family);
        counter.link("layered_" + family, "cut_" + family);
        counter.link(family + "_pillar", "cut_" + family);
        counter.put(byId("cut_" + family), raw);

        for (int i = 0; i < SHAPES.length; i++) {
            String shape = SHAPES[i];
            counter.link("small_" + family + "_brick" + shape, "cut_" + family + "_brick" + shape);
            counter.link("cut_" + family + "_brick" + shape, "cut_" + family + shape);
            counter.link("polished_cut_" + family + shape, "cut_" + family + shape);
            counter.put(byId("cut_" + family + shape), rawShapes == null ? null : rawShapes[i]);
        }
    }

    @Nullable
    private static Block byId(String path) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(CREATE, path));
        return block == Blocks.AIR ? null : block;
    }

    private static final class Counter {
        private final BiConsumer<Block, Block> sink;
        private final Map<Block, Block> added = new HashMap<>();
        private final List<String> missing = new ArrayList<>();
        private int linked;

        Counter(BiConsumer<Block, Block> sink) {
            this.sink = sink;
        }

        void link(String fromPath, String toPath) {
            Block from = byId(fromPath);
            Block to = byId(toPath);
            if (from == null) {
                missing.add(CREATE + ":" + fromPath);
                return;
            }
            if (to == null) {
                missing.add(CREATE + ":" + toPath);
                return;
            }
            put(from, to);
        }

        void put(@Nullable Block from, @Nullable Block to) {
            if (from == null || to == null) {
                return;
            }
            sink.accept(from, to);
            added.put(from, to);
            linked++;
        }

        void report() {
            if (missing.isEmpty()) {
                LOGGER.info("World Degrade: registered {} Create wear steps (longest chain {})",
                        linked, longestChain());
            } else {
                LOGGER.warn("World Degrade: registered {} Create wear steps, but {} id(s) did not "
                                + "resolve — Create's naming may have changed: {}",
                        linked, missing.size(), missing);
            }
            for (Block start : cycles()) {
                LOGGER.error("World Degrade: Create wear chain from {} loops, so those blocks can "
                                + "never wear out and will never collapse",
                        BuiltInRegistries.BLOCK.getKey(start));
            }
        }

        private int longestChain() {
            int longest = 0;
            for (Block start : added.keySet()) {
                int length = 0;
                Block current = start;
                Set<Block> seen = new HashSet<>();
                while (current != null && seen.add(current)) {
                    Block next = dev.ncn.worlddegrade.degrade.effects.BrickWeatherEffect
                            .wearTarget(current);
                    if (next == null) {
                        break;
                    }
                    length++;
                    current = next;
                }
                longest = Math.max(longest, length);
            }
            return longest;
        }

        private List<Block> cycles() {
            List<Block> looping = new ArrayList<>();
            for (Block start : added.keySet()) {
                Block current = start;
                Set<Block> seen = new HashSet<>();
                while (current != null && seen.add(current)) {
                    current = dev.ncn.worlddegrade.degrade.effects.BrickWeatherEffect
                            .wearTarget(current);
                }
                if (current != null) {
                    looping.add(start);
                }
            }
            return looping;
        }
    }
}
