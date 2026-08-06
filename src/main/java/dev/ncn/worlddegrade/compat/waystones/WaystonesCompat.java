package dev.ncn.worlddegrade.compat.waystones;

import dev.ncn.worlddegrade.compat.ModCompat;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class WaystonesCompat implements ModCompat {

    @Override
    public String modId() {
        return "waystones";
    }

    @Override
    public void init() {
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
    }

    @Override
    public List<DegradeEffect> createEffects() {
        return List.of(new WaystoneDecayEffect());
    }

    @Override
    public void onUndo(MinecraftServer server, CompoundTag compatSection) {
        WaystoneRevocations queues = WaystoneRevocations.get(server);
        ListTag revoked = compatSection.getList("revoked", Tag.TAG_COMPOUND);
        for (int i = 0; i < revoked.size(); i++) {
            UUID waystoneUid = NbtUtils.loadUUID(revoked.getCompound(i).get("waystone"));
            WaystoneRevocations.Revocation revocation = queues.removeRevocation(waystoneUid);
            if (revocation == null) {
                continue;
            }
            Optional<Waystone> waystone = WaystonesAPI.getWaystone(server, waystoneUid);
            Set<UUID> offline = new HashSet<>();
            for (UUID playerUuid : revocation.appliedPlayers) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
                if (player != null && waystone.isPresent()) {
                    PlayerWaystoneManager.activateWaystone(player, waystone.get());
                } else {
                    offline.add(playerUuid);
                }
            }
            queues.addRestoration(waystoneUid, offline);
        }
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        WaystoneRevocations queues = WaystoneRevocations.get(server);

        for (Iterator<WaystoneRevocations.Revocation> it = queues.revocations.iterator(); it.hasNext(); ) {
            WaystoneRevocations.Revocation revocation = it.next();
            if (revocation.appliedPlayers.contains(player.getUUID())) {
                continue;
            }
            Optional<Waystone> waystone = WaystonesAPI.getWaystone(server, revocation.waystoneUid);
            if (waystone.isEmpty()) {
                it.remove();
                queues.setDirty();
                continue;
            }
            if (PlayerWaystoneManager.isWaystoneActivated(player, waystone.get())) {
                PlayerWaystoneManager.deactivateWaystone(player, waystone.get());
            }
            revocation.appliedPlayers.add(player.getUUID());
            queues.setDirty();
        }

        for (Iterator<WaystoneRevocations.Restoration> it = queues.restorations.iterator(); it.hasNext(); ) {
            WaystoneRevocations.Restoration restoration = it.next();
            if (!restoration.players.remove(player.getUUID())) {
                continue;
            }
            WaystonesAPI.getWaystone(server, restoration.waystoneUid)
                    .ifPresent(waystone -> PlayerWaystoneManager.activateWaystone(player, waystone));
            if (restoration.players.isEmpty()) {
                it.remove();
            }
            queues.setDirty();
        }
    }
}
