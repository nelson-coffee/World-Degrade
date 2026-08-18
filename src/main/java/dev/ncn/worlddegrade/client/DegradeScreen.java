package dev.ncn.worlddegrade.client;

import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import dev.ncn.worlddegrade.net.DegradeRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

public class DegradeScreen extends Screen {
    private static final int WIDGET_WIDTH = 220;
    private static final int WIDGET_HEIGHT = 20;
    private static final int DEFAULT_RADIUS = 64;

    private int selectedLevel = WorldDegradeConfig.defaultLevel();
    private boolean wholeWorld = false;
    private boolean corruptComputers = true;
    private EditBox radiusBox;
    private int layoutTop;
    @org.jetbrains.annotations.Nullable
    private float[] customChances;

    public DegradeScreen() {
        super(Component.translatable("gui.worlddegrade.title"));
    }

    int selectedLevel() {
        return selectedLevel;
    }

    void setCustomChances(@org.jetbrains.annotations.Nullable float[] customChances) {
        this.customChances = customChances;
    }

    @Override
    protected void init() {
        int x = width / 2 - WIDGET_WIDTH / 2;
        int y = height / 2 - 75;
        layoutTop = y;

        addRenderableWidget(new LevelSlider(x, y));

        addRenderableWidget(Button.builder(Component.translatable("gui.worlddegrade.advanced"), button -> {
            float[] initial = customChances != null ? customChances
                    : dev.ncn.worlddegrade.degrade.DegradeChances.of(
                            dev.ncn.worlddegrade.degrade.DegradeLevel.byId(selectedLevel)).toArray();
            minecraft.setScreen(new AdvancedDegradeScreen(this, initial));
        }).bounds(x, y + 26, WIDGET_WIDTH, WIDGET_HEIGHT).build());

        addRenderableWidget(CycleButton.booleanBuilder(
                        Component.translatable("gui.worlddegrade.mode.world"),
                        Component.translatable("gui.worlddegrade.mode.radius"))
                .withInitialValue(wholeWorld)
                .create(x, y + 52, WIDGET_WIDTH, WIDGET_HEIGHT,
                        Component.translatable("gui.worlddegrade.mode"),
                        (button, value) -> {
                            wholeWorld = value;
                            radiusBox.visible = !value;
                        }));

        radiusBox = new EditBox(font, x, y + 78, WIDGET_WIDTH, WIDGET_HEIGHT,
                Component.translatable("gui.worlddegrade.radius"));
        radiusBox.setValue(String.valueOf(DEFAULT_RADIUS));
        radiusBox.setHint(Component.translatable("gui.worlddegrade.radius.hint"));
        radiusBox.setFilter(text -> text.matches("\\d{0,4}"));
        radiusBox.visible = !wholeWorld;
        addRenderableWidget(radiusBox);

        int buttonRow = y + 114;
        if (net.neoforged.fml.ModList.get().isLoaded("computercraft")) {
            addRenderableWidget(net.minecraft.client.gui.components.Checkbox
                    .builder(Component.translatable("gui.worlddegrade.corrupt_computers"), font)
                    .pos(x, y + 104)
                    .selected(corruptComputers)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable("gui.worlddegrade.corrupt_computers.tooltip")))
                    .onValueChange((box, value) -> corruptComputers = value)
                    .build());
            buttonRow = y + 130;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.worlddegrade.confirm"), button -> confirm())
                .bounds(x, buttonRow, WIDGET_WIDTH / 2 - 2, WIDGET_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.worlddegrade.cancel"), button -> onClose())
                .bounds(x + WIDGET_WIDTH / 2 + 2, buttonRow, WIDGET_WIDTH / 2 - 2, WIDGET_HEIGHT).build());
    }

    private void confirm() {
        int radius = DEFAULT_RADIUS;
        try {
            radius = Integer.parseInt(radiusBox.getValue());
        } catch (NumberFormatException ignored) {
        }
        PacketDistributor.sendToServer(
                new DegradeRequestPayload(selectedLevel, wholeWorld, radius, corruptComputers, customChances));
        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, layoutTop - 25, 0xFFFFFF);
        if (customChances != null) {
            guiGraphics.drawCenteredString(font, Component.translatable("gui.worlddegrade.adv.active"),
                    width / 2, layoutTop - 13, 0xFFAA00);
        }
        if (wholeWorld) {
            guiGraphics.drawCenteredString(font, Component.translatable("gui.worlddegrade.warning"),
                    width / 2, layoutTop + 84, 0xFF5555);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class LevelSlider extends AbstractSliderButton {
        LevelSlider(int x, int y) {
            super(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(), (selectedLevel - 1) / 4.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.worlddegrade.level", selectedLevel,
                    Component.translatable("gui.worlddegrade.tier." + selectedLevel)));
        }

        @Override
        protected void applyValue() {
            selectedLevel = 1 + (int) Math.round(value * 4);
            selectedLevel = Mth.clamp(selectedLevel, 1, 5);
            value = (selectedLevel - 1) / 4.0;
            updateMessage();
        }
    }
}
