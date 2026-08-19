package dev.ncn.worlddegrade.compat.opac;

import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.common.claims.api.SpecialClaimOwners;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.tracker.api.IClaimsManagerListenerAPI;

/**
 * Registered with OPAC's claims tracker; the only claim-change signal OPAC exposes for expiration
 * (there is no dedicated expiration event). When a claim expires OPAC rewrites the chunk's owner to
 * its {@link SpecialClaimOwners#EXPIRED} pseudo-player, one {@code onChunkChange} per chunk spread over
 * many ticks, and this collects those into {@link ExpiredChunkBatcher} for the compat to drain.
 *
 * <p>Only the expired owner is acted on: a {@code null} claim (an unclaim — including our own
 * after-degradation unclaim) or any other owner is ignored, which is also what stops our unclaim calls
 * from feeding back into a loop. State is never mutated from inside the callback (OPAC iterates its
 * listeners over a plain set); the batcher is only drained on the following server tick.
 */
public final class ClaimExpirationListener implements IClaimsManagerListenerAPI {

    private final MinecraftServer server;
    private final ExpiredChunkBatcher batcher;

    ClaimExpirationListener(MinecraftServer server, ExpiredChunkBatcher batcher) {
        this.server = server;
        this.batcher = batcher;
    }

    @Override
    public void onChunkChange(ResourceLocation dimension, int chunkX, int chunkZ,
                              IPlayerChunkClaimAPI claim) {
        if (!WorldDegradeConfig.opacEnabled()) {
            return;
        }
        if (claim == null || !SpecialClaimOwners.EXPIRED.equals(claim.getPlayerId())) {
            return;
        }
        batcher.add(dimension, ChunkPos.asLong(chunkX, chunkZ), server.getTickCount());
    }

    @Override
    public void onWholeRegionChange(ResourceLocation dimension, int regionX, int regionZ) {
    }

    @Override
    public void onDimensionChange(ResourceLocation dimension) {
    }
}
