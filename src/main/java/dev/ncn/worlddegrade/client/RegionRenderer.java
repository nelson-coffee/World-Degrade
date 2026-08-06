package dev.ncn.worlddegrade.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ncn.worlddegrade.WorldDegrade;
import dev.ncn.worlddegrade.item.ModItems;
import dev.ncn.worlddegrade.marking.MarkedRegions;
import dev.ncn.worlddegrade.net.MarkingPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = WorldDegrade.MOD_ID, value = Dist.CLIENT)
public final class RegionRenderer {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !holdingWand(player)) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (MarkedRegions.Region region : MarkedRegionsClientCache.regions()) {
            drawBox(poseStack, buffers, region.bounds(), 1.0f, 0.55f, 0.1f);
        }
        BlockPos first = MarkedRegionsClientCache.selectionFirst();
        if (first != null) {
            BlockPos second = MarkedRegionsClientCache.selectionSecond();
            AABB selection = second == null
                    ? new AABB(first)
                    : AABB.encapsulatingFullBlocks(first, second);
            drawBox(poseStack, buffers, selection, 0.2f, 0.8f, 1.0f);
        }

        buffers.endBatch();
        poseStack.popPose();
    }

    private static void drawBox(PoseStack poseStack, MultiBufferSource buffers, AABB box,
                                float red, float green, float blue) {
        DebugRenderer.renderFilledBox(poseStack, buffers, box, red, green, blue, 0.25f);
        LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), box,
                red, green, blue, 1.0f);
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getEntity().isShiftKeyDown() && event.getItemStack().is(ModItems.MARKER_WAND.get())) {
            PacketDistributor.sendToServer(new MarkingPayloads.WandAirAttack());
        }
    }

    private static boolean holdingWand(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.MARKER_WAND.get())
                || player.getOffhandItem().is(ModItems.MARKER_WAND.get());
    }

    private RegionRenderer() {
    }
}
