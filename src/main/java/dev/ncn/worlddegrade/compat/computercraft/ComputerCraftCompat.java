package dev.ncn.worlddegrade.compat.computercraft;

import com.mojang.logging.LogUtils;
import dev.ncn.worlddegrade.compat.ModCompat;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.List;

public class ComputerCraftCompat implements ModCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String modId() {
        return "computercraft";
    }

    @Override
    public List<DegradeEffect> createEffects() {
        return List.of(new ComputerCorruptionEffect());
    }

    @Override
    public void onUndo(MinecraftServer server, CompoundTag compatSection) {
        if (!compatSection.contains(ComputerCorruptionEffect.UNDO_FILES)) {
            return;
        }
        int restored = FileGarbler.restore(
                compatSection.getList(ComputerCorruptionEffect.UNDO_FILES, 10));
        if (restored > 0) {
            LOGGER.info("World Degrade: restored {} corrupted computer file(s)", restored);
        }
    }
}
