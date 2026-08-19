package dev.ncn.worlddegrade.config;

import com.mojang.logging.LogUtils;
import dev.ncn.worlddegrade.WorldDegrade;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = WorldDegrade.MOD_ID)
public final class WorldDegradeConfig {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Set<String> disabledDimensions = Set.of();

    private WorldDegradeConfig() {
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
    }

    public static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ServerConfig.SPEC) {
            refreshDisabledDimensions();
        }
    }

    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ServerConfig.SPEC) {
            refreshDisabledDimensions();
        }
    }

    public static void onUnload(ModConfigEvent.Unloading event) {
        if (event.getConfig().getSpec() == ServerConfig.SPEC) {
            disabledDimensions = Set.of();
        }
    }

    private static void refreshDisabledDimensions() {
        disabledDimensions = normalizeDimensions(ServerConfig.CONFIG.disabledDimensions.get());
    }

    /**
     * Parses raw {@code disabledDimensions} entries into canonical {@code namespace:path} ids.
     * Unparseable entries are logged and skipped. Pure so it can be unit-tested without a server.
     */
    static Set<String> normalizeDimensions(List<? extends String> raw) {
        Set<String> parsed = new HashSet<>();
        for (String entry : raw) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id == null) {
                LOGGER.warn("World Degrade: ignoring invalid dimension id in disabledDimensions: '{}'", entry);
                continue;
            }
            parsed.add(id.toString());
        }
        return parsed;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Set<String> known = new HashSet<>();
        for (var key : server.levelKeys()) {
            known.add(key.location().toString());
        }
        for (String dimension : disabledDimensions) {
            if (!known.contains(dimension)) {
                LOGGER.warn("World Degrade: disabledDimensions lists '{}', which is not a loaded dimension on this server", dimension);
            }
        }
    }

    public static boolean isDimensionTracked(ServerLevel level) {
        return !isDimensionDisabled(level);
    }

    public static boolean isDimensionDisabled(ServerLevel level) {
        return disabledDimensions.contains(level.dimension().location().toString());
    }

    public static boolean placementTrackingEnabled() {
        return ServerConfig.CONFIG.enablePlacementTracking.get();
    }

    public static boolean excavationTrackingEnabled() {
        return ServerConfig.CONFIG.enableExcavationTracking.get();
    }

    public static int defaultLevel() {
        return ServerConfig.CONFIG.defaultLevel.get();
    }

    public static int chunksPerTick() {
        return ServerConfig.CONFIG.chunksPerTick.get();
    }

    public static boolean burntBlockVariantsEnabled() {
        return ServerConfig.CONFIG.enableBurntBlockVariants.get();
    }

    public static boolean woodRotEnabled() {
        return ServerConfig.CONFIG.enableWoodRot.get();
    }

    public static boolean glassBreakEnabled() {
        return ServerConfig.CONFIG.enableGlassBreak.get();
    }

    public static boolean brickWeatherEnabled() {
        return ServerConfig.CONFIG.enableBrickWeather.get();
    }

    public static boolean structuralCollapseEnabled() {
        return ServerConfig.CONFIG.enableStructuralCollapse.get();
    }

    public static boolean doorBreakEnabled() {
        return ServerConfig.CONFIG.enableDoorBreak.get();
    }

    public static boolean containerLootEnabled() {
        return ServerConfig.CONFIG.enableContainerLoot.get();
    }

    public static boolean lightSnuffEnabled() {
        return ServerConfig.CONFIG.enableLightSnuff.get();
    }

    public static boolean portalBreakEnabled() {
        return ServerConfig.CONFIG.enablePortalBreak.get();
    }

    public static boolean overgrowthEnabled() {
        return ServerConfig.CONFIG.enableOvergrowth.get();
    }

    public static boolean leafGrowthEnabled() {
        return ServerConfig.CONFIG.enableLeafGrowth.get();
    }

    public static boolean vanillaDecayEnabled() {
        return ServerConfig.CONFIG.enableVanillaDecay.get();
    }

    public static boolean unknownBreakEnabled() {
        return ServerConfig.CONFIG.enableUnknownBreak.get();
    }
}
