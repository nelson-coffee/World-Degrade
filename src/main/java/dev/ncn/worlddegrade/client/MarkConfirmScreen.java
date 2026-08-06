package dev.ncn.worlddegrade.client;

import dev.ncn.worlddegrade.net.MarkingPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class MarkConfirmScreen extends Screen {
    private final BlockPos min;
    private final BlockPos max;
    private final int blockCount;

    public MarkConfirmScreen(BlockPos min, BlockPos max, int blockCount) {
        super(Component.translatable("gui.worlddegrade.mark.title"));
        this.min = min;
        this.max = max;
        this.blockCount = blockCount;
    }

    @Override
    protected void init() {
        int buttonY = height / 2 + 10;
        addRenderableWidget(Button.builder(Component.translatable("gui.worlddegrade.confirm"), button -> {
            PacketDistributor.sendToServer(new MarkingPayloads.ConfirmMark());
            onClose();
        }).bounds(width / 2 - 102, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.worlddegrade.cancel"), button -> onClose())
                .bounds(width / 2 + 2, buttonY, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xFFFFFF);
        guiGraphics.drawCenteredString(font, Component.translatable("gui.worlddegrade.mark.size",
                        max.getX() - min.getX() + 1, max.getY() - min.getY() + 1,
                        max.getZ() - min.getZ() + 1, blockCount),
                width / 2, height / 2 - 20, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
