package dev.ncn.worlddegrade.client;

import dev.ncn.worlddegrade.degrade.DegradeChances;
import dev.ncn.worlddegrade.degrade.DegradeLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AdvancedDegradeScreen extends Screen {
    private static final String[] VALUE_KEYS = {
            "roof", "wall", "glass", "brick", "door", "wood", "keep",
            "vine", "cobweb", "machine", "envelope", "campfire", "film", "other"};

    private static final int SLIDER_WIDTH = 150;
    private static final int SLIDER_HEIGHT = 20;
    private static final int ROW_SPACING = 24;
    private static final int COLUMN_GAP = 8;

    private static final java.util.Map<String, String> MOD_ONLY = java.util.Map.of(
            "film", "exposure",
            "envelope", "aeronautics");

    private final DegradeScreen parent;
    private final float[] values;
    private int layoutTop;

    public AdvancedDegradeScreen(DegradeScreen parent, float[] initialValues) {
        super(Component.translatable("gui.worlddegrade.adv.title"));
        this.parent = parent;
        this.values = initialValues.clone();
    }

    @Override
    protected void init() {
        int leftX = width / 2 - SLIDER_WIDTH - COLUMN_GAP / 2;
        int rightX = width / 2 + COLUMN_GAP / 2;

        int[] shown = visibleIndices();
        int rows = (shown.length + 1) / 2;
        int contentHeight = rows * ROW_SPACING + 8 + SLIDER_HEIGHT;
        layoutTop = height / 2 - contentHeight / 2;
        int startY = layoutTop;

        for (int slot = 0; slot < shown.length; slot++) {
            int x = (slot % 2 == 0) ? leftX : rightX;
            int y = startY + (slot / 2) * ROW_SPACING;
            addRenderableWidget(new ChanceSlider(shown[slot], x, y));
        }

        int buttonY = startY + rows * ROW_SPACING + 8;
        int buttonWidth = 100;
        int totalWidth = buttonWidth * 3 + 8;
        int buttonX = width / 2 - totalWidth / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.worlddegrade.confirm"), button -> done())
                .bounds(buttonX, buttonY, buttonWidth, SLIDER_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.worlddegrade.adv.reset"), button -> reset())
                .bounds(buttonX + buttonWidth + 4, buttonY, buttonWidth, SLIDER_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.worlddegrade.cancel"), button -> onClose())
                .bounds(buttonX + (buttonWidth + 4) * 2, buttonY, buttonWidth, SLIDER_HEIGHT).build());
    }

    private static int[] visibleIndices() {
        int[] shown = new int[DegradeChances.VALUE_COUNT];
        int count = 0;
        for (int i = 0; i < DegradeChances.VALUE_COUNT; i++) {
            String requiredMod = MOD_ONLY.get(VALUE_KEYS[i]);
            if (requiredMod == null || net.neoforged.fml.ModList.get().isLoaded(requiredMod)) {
                shown[count++] = i;
            }
        }
        return java.util.Arrays.copyOf(shown, count);
    }

    private void done() {
        parent.setCustomChances(values.clone());
        minecraft.setScreen(parent);
    }

    private void reset() {
        float[] presets = DegradeChances.of(DegradeLevel.byId(parent.selectedLevel())).toArray();
        System.arraycopy(presets, 0, values, 0, values.length);
        parent.setCustomChances(null);
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, layoutTop - 18, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class ChanceSlider extends AbstractSliderButton {
        private final int index;

        ChanceSlider(int index, int x, int y) {
            super(x, y, SLIDER_WIDTH, SLIDER_HEIGHT, Component.empty(), values[index]);
            this.index = index;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.worlddegrade.adv." + VALUE_KEYS[index],
                    Math.round(values[index] * 100.0f)));
        }

        @Override
        protected void applyValue() {
            values[index] = Math.round(value * 100.0) / 100.0f;
            value = values[index];
            updateMessage();
        }
    }
}
