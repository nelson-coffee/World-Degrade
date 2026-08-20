package dev.ncn.worlddegrade.data;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Scans the live block registry once, on demand, and writes a datapack of auto-detected wear chains
 * into the world folder. The heavy lifting is naming-convention heuristics: modded stone-like blocks
 * usually follow the same {@code polished_/chiseled_/...} conventions as vanilla, so most of them can
 * be wired up without any per-mod compat code.
 *
 * <p>Output splits into two trees: {@code chains/} holds the active auto-detected modded chains (the
 * mod's reload listener loads these), and {@code chains_reference/} holds the built-in vanilla chains
 * written out for the admin to copy-and-edit (never loaded).
 */
public final class DatapackGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PACK_DIR = "worlddegrade-generated";
    private static final int PACK_FORMAT = 48; // 1.21.1 data pack format

    private static final String[] STRIP_PREFIXES = {"polished_", "smooth_", "cut_", "waxed_"};

    public record Summary(int chains, int namespaces, Path packRoot) {
    }

    private DatapackGenerator() {
    }

    public static Summary generate(MinecraftServer server) throws IOException {
        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_DIR);
        deleteTree(packRoot);

        Path chainsDir = packRoot.resolve("data/worlddegrade/chains");
        Path referenceDir = packRoot.resolve("data/worlddegrade/chains_reference");
        Files.createDirectories(chainsDir);
        Files.createDirectories(referenceDir);

        writePackMeta(packRoot);

        // First collect every detected block -> target link, then stitch them into chains. Detection
        // yields a single target per block, so the links form a functional graph that walks cleanly
        // into linear sequences (see buildChains).
        Map<Block, Block> moddedLinks = new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id.getNamespace().equals("minecraft")) {
                continue; // vanilla is covered by the built-in defaults + the reference dump
            }
            Block target = detectTarget(id);
            if (target != null) {
                moddedLinks.put(block, target);
            }
        }

        Set<String> namespaces = new TreeSet<>();
        int chains = 0;
        for (List<Block> chain : buildChains(moddedLinks)) {
            ResourceLocation head = BuiltInRegistries.BLOCK.getKey(chain.get(0));
            // One file per namespace subfolder keeps a modpack's output browsable instead of dumping
            // thousands of files into a single directory.
            writeWearChain(chainsDir.resolve(head.getNamespace()), head, blockIds(chain));
            namespaces.add(head.getNamespace());
            chains++;
        }

        writeReferenceChains(referenceDir);

        LOGGER.info("World Degrade: generate found {} wear chains across {} namespaces", chains, namespaces.size());
        LOGGER.warn("World Degrade: /worlddegrade generate overwrote {} — back up manual edits in chains/ before re-running",
                packRoot);
        return new Summary(chains, namespaces.size(), packRoot);
    }

    /**
     * Stitches a block -> target link map into linear chains.
     *
     * <p>A chain starts at a <em>head</em> — a block that nothing else degrades into — and follows each
     * block's single target until the trail ends (a block with no onward link) or loops. So a file
     * holds a <em>longer chain</em> when the detected links connect transitively
     * (e.g. {@code chiseled_x -> cracked_x -> x}), and a <em>short/separate</em> two-block chain when a
     * head's target has no onward link of its own. Blocks reached from another block are never chain
     * heads, so they are emitted once as part of their predecessor's chain rather than in a file of
     * their own.
     *
     * <p>When two heads converge on a shared tail ({@code a -> c}, {@code b -> c -> d}), the tail is
     * walked only by the first chain to reach it; later chains stop at the shared block (keeping the
     * link into it) so no block is written into more than one file. Heads are processed in a stable
     * order so which chain owns a shared tail does not vary between runs.
     */
    private static List<List<Block>> buildChains(Map<Block, Block> links) {
        Set<Block> targets = new HashSet<>(links.values());
        List<Block> heads = new ArrayList<>();
        for (Block head : links.keySet()) {
            if (!targets.contains(head)) {
                heads.add(head);
            }
        }
        heads.sort(Comparator.comparing(head -> BuiltInRegistries.BLOCK.getKey(head).toString()));
        List<List<Block>> chains = new ArrayList<>();
        Set<Block> emitted = new HashSet<>();
        for (Block head : heads) {
            List<Block> chain = new ArrayList<>();
            Block current = head;
            while (current != null && !chain.contains(current)) {
                chain.add(current);
                if (emitted.contains(current)) {
                    break;
                }
                current = links.get(current);
            }
            if (chain.size() >= 2) {
                chains.add(chain);
                emitted.addAll(chain);
            }
        }
        return chains;
    }

    private static List<String> blockIds(List<Block> chain) {
        List<String> ids = new ArrayList<>();
        for (Block block : chain) {
            ids.add(BuiltInRegistries.BLOCK.getKey(block).toString());
        }
        return ids;
    }

    /**
     * Applies the naming heuristics in the documented order
     * ({@code chiseled/polished/smooth/cut/waxed -> base -> cracked/cobbled/mossy}), only ever mapping
     * within the same namespace and only when the candidate target actually exists.
     */
    @Nullable
    private static Block detectTarget(ResourceLocation id) {
        String namespace = id.getNamespace();
        String path = id.getPath();

        if (path.startsWith("chiseled_")) {
            Block cracked = block(namespace, "cracked_" + path.substring("chiseled_".length()));
            if (cracked != null) {
                return cracked;
            }
        }
        for (String prefix : STRIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                Block base = block(namespace, path.substring(prefix.length()));
                if (base != null) {
                    return base;
                }
            }
        }
        Block cracked = block(namespace, "cracked_" + path);
        if (cracked != null) {
            return cracked;
        }
        Block cobbled = block(namespace, "cobbled_" + path);
        if (cobbled != null) {
            return cobbled;
        }
        Block mossy = block(namespace, "mossy_" + path);
        if (mossy != null) {
            return mossy;
        }
        return null;
    }

    private static void writeReferenceChains(Path referenceDir) throws IOException {
        for (List<Block> chain : buildChains(WearChains.builtinEntries())) {
            ResourceLocation head = BuiltInRegistries.BLOCK.getKey(chain.get(0));
            writeWearChain(referenceDir.resolve(head.getNamespace()), head, blockIds(chain));
        }
    }

    private static void writeWearChain(Path namespaceDir, ResourceLocation head, List<String> chain) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("type", "worlddegrade:wear_chain");
        JsonArray array = new JsonArray();
        for (String id : chain) {
            array.add(id);
        }
        json.add("chain", array);
        // The file already lives under a <namespace>/ subfolder, so the name is just the head's path.
        String fileName = head.getPath().replaceAll("[^a-z0-9_]", "_") + ".json";
        write(namespaceDir.resolve(fileName), new GsonBuilder().setPrettyPrinting().create().toJson(json));
    }

    private static void writePackMeta(Path packRoot) throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("description", "World Degrade generated wear chains");
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        write(packRoot.resolve("pack.mcmeta"), new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }

    @Nullable
    private static Block block(String namespace, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block == Blocks.AIR ? null : block;
    }
}
