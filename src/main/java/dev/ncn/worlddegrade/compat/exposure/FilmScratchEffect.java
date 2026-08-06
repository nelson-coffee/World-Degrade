package dev.ncn.worlddegrade.compat.exposure;

import com.mojang.logging.LogUtils;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.DegradeLevel;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.ExposureServer;
import io.github.mortuusars.exposure.data.ColorPalette;
import io.github.mortuusars.exposure.util.ExtraData;
import io.github.mortuusars.exposure.world.camera.ExposureType;
import io.github.mortuusars.exposure.data.ColorPalettes;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.item.FilmItem;
import io.github.mortuusars.exposure.world.level.storage.ExposureData;
import io.github.mortuusars.exposure.world.level.storage.ExposureIdentifier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class FilmScratchEffect implements DegradeEffect {
    private static final int SCRATCH_ARGB = 0xFFEFE9DC;
    private static final int GRIME_ARGB = 0xFF241E18;

    private static final float FADE_STEP = 0.75f;
    private static final float FLAT_STEP = 0.45f;
    private static final float CAST_STEP = 0.60f;

    private static final Logger LOGGER = LogUtils.getLogger();

    private ScratchedExposureRegistry registry;
    private int passRun = -1;

    private int currentPass(DegradeContext ctx) {
        if (passRun < 0) {
            registry = ScratchedExposureRegistry.get(ctx.level.getServer());
            passRun = registry.beginPass();
            purgeExpired();
        }
        return passRun;
    }

    private void purgeExpired() {
        for (String expired : registry.collectExpired()) {
            try {
                ExposureServer.exposureRepository().delete(expired);
            } catch (Exception e) {
                LOGGER.warn("World Degrade: could not delete superseded film picture {}", expired, e);
            }
            registry.forget(expired);
        }
    }

    @Override
    public void apply(DegradeContext ctx) {
        float chance = ctx.chances.filmDamageChance();
        if (chance <= 0.0f) {
            return;
        }
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            if (ctx.blockEntity(pos) == null) {
                continue;
            }
            IItemHandler handler = ctx.level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (!(handler instanceof IItemHandlerModifiable inventory)) {
                continue;
            }
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty() || !(stack.getItem() instanceof FilmItem film)) {
                    continue;
                }
                List<Frame> frames = film.getStoredFrames(stack);
                if (frames.isEmpty() || !ctx.roll(chance)) {
                    continue;
                }
                currentPass(ctx);
                List<Frame> scratched = scratchFrames(ctx, frames, chance);
                if (scratched != null) {
                    ctx.recordForUndo(pos);
                    stack.set(Exposure.DataComponents.FILM_FRAMES, scratched);
                    ctx.markChanged();
                }
            }
        }
    }

    private record ColourStep(float fade, float flat, float[] cast) {
    }

    private List<Frame> scratchFrames(DegradeContext ctx, List<Frame> frames, float chance) {
        List<Frame> result = new ArrayList<>(frames.size());
        boolean changed = false;
        int cast = rollCast(ctx, frames);

        for (Frame frame : frames) {
            ExtraData aged = frame.getExtraDataForReading();
            float fade = aged.getOrDefault(ColourAge.FADE, 0.0f);
            float flat = aged.getOrDefault(ColourAge.FLAT, 0.0f);
            float castStrength = aged.getOrDefault(ColourAge.CAST_STRENGTH, 0.0f);
            boolean colour = frame.type() == ExposureType.COLOR;
            ColourStep step = colour ? colourStep(fade, flat, castStrength, chance, cast) : null;

            String scratchedId = scratchExposure(ctx, frame.identifier(), chance, step);
            if (scratchedId == null) {
                result.add(frame);
                continue;
            }
            Frame.Mutable rebuilt = frame.toMutable()
                    .setIdentifier(ExposureIdentifier.id(scratchedId));
            if (colour) {
                rebuilt.addExtraData(ColourAge.CAST, cast)
                        .addExtraData(ColourAge.FADE,
                                ColourAge.advance(fade, chance * FADE_STEP, ColourAge.FADE_CAP))
                        .addExtraData(ColourAge.FLAT,
                                ColourAge.advance(flat, chance * FLAT_STEP, ColourAge.FLAT_CAP))
                        .addExtraData(ColourAge.CAST_STRENGTH,
                                ColourAge.advance(castStrength, chance * CAST_STEP, ColourAge.CAST_CAP));
            }
            result.add(rebuilt.toImmutable());
            changed = true;
        }
        return changed ? result : null;
    }

    private int rollCast(DegradeContext ctx, List<Frame> frames) {
        for (Frame frame : frames) {
            int stored = frame.getExtraDataForReading().getOrDefault(ColourAge.CAST, -1);
            if (stored >= 0) {
                return stored;
            }
        }
        return ColourAge.rollCast(ctx.random);
    }

    private ColourStep colourStep(float fade, float flat, float castStrength, float chance, int cast) {
        return new ColourStep(
                ColourAge.step(fade, chance * FADE_STEP, ColourAge.FADE_CAP),
                ColourAge.step(flat, chance * FLAT_STEP, ColourAge.FLAT_CAP),
                ColourAge.castFactors(cast, ColourAge.step(castStrength, chance * CAST_STEP, ColourAge.CAST_CAP)));
    }

    private String scratchExposure(DegradeContext ctx, ExposureIdentifier identifier, float chance,
                                   ColourStep colour) {
        if (identifier == null || !identifier.isId()) {
            return null;
        }
        Optional<String> id = identifier.getId();
        if (id.isEmpty()) {
            return null;
        }
        Optional<ExposureData> loaded = ExposureServer.exposureRepository().load(id.get()).getData();
        if (loaded.isEmpty()) {
            return null;
        }
        ExposureData data = loaded.get();
        int width = data.getWidth();
        int height = data.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        byte[] pixels = data.getPixels().clone();

        ScratchCanvas canvas = new ScratchCanvas(pixels, width, height, resolvePalette(ctx, data));
        if (colour != null) {
            canvas.ageColour(ctx.random, colour.fade(), colour.flat(), colour.cast());
        }
        damage(canvas, ctx.random, chance, ctx.chances.levelId());

        String newId = ScratchedExposureRegistry.SCRATCHED_PREFIX + UUID.randomUUID();
        ExposureServer.exposureRepository().save(newId,
                new ExposureData(width, height, pixels, data.getPaletteId(), data.getTag()));
        if (id.get().startsWith(ScratchedExposureRegistry.SCRATCHED_PREFIX)) {
            registry.markSuperseded(id.get(), passRun);
        }
        return newId;
    }

    private ColorPalette resolvePalette(DegradeContext ctx, ExposureData data) {
        var registries = ctx.level.registryAccess();
        if (data.getPaletteId() != null) {
            try {
                return ColorPalettes.get(registries, data.getPaletteId()).value();
            } catch (Exception ignored) {
            }
        }
        return ColorPalettes.getDefault(registries).value();
    }

    private void damage(ScratchCanvas canvas, RandomSource random, float chance, int levelId) {
        int height = canvas.height();
        int prominent = 1 + Math.round(chance * 2.0f);
        for (int i = 0; i < prominent; i++) {
            if (random.nextInt(3) == 0) {
                drawMark(canvas, random, GRIME_ARGB, 0.25f + random.nextFloat() * 0.35f,
                        Math.round(height * (0.35f + random.nextFloat() * 0.35f)));
            } else {
                drawMark(canvas, random, SCRATCH_ARGB, 0.35f + random.nextFloat() * 0.45f,
                        Math.round(height * (0.45f + random.nextFloat() * 0.5f)));
            }
        }
        int faint = Math.round(chance * 3.0f);
        for (int i = 0; i < faint; i++) {
            drawMark(canvas, random, SCRATCH_ARGB, 0.12f + random.nextFloat() * 0.18f,
                    Math.round(height * (0.4f + random.nextFloat() * 0.55f)));
        }
        int nicks = Math.round(chance * 2.0f);
        for (int i = 0; i < nicks; i++) {
            drawMark(canvas, random, SCRATCH_ARGB, 0.4f + random.nextFloat() * 0.5f,
                    Math.round(height * (0.1f + random.nextFloat() * 0.15f)));
        }
        drawDust(canvas, random, chance);
        if (levelId >= DegradeLevel.RUINED.id()) {
            EmulsionDecay.apply(canvas, random, chance);
        }
        canvas.grain(random, 0.012f * (0.4f + chance));
    }

    private void drawMark(ScratchCanvas canvas, RandomSource random, int argb,
                          float peak, int length) {
        int height = canvas.height();
        length = Mth.clamp(length, 3, height);
        int startY = random.nextInt(Math.max(1, height - length + 1));
        float x = random.nextFloat() * canvas.width();
        float heading = (random.nextFloat() - 0.5f) * 2.0f;
        float slope = heading;
        float dropout = 0.04f + random.nextFloat() * 0.10f;

        for (int step = 0; step < length; step++) {
            slope = Mth.clamp(slope + (random.nextFloat() - 0.5f) * 0.04f,
                    heading - 0.2f, heading + 0.2f);
            x += slope;
            if (x < -1.0f || x > canvas.width()) {
                return;
            }
            if (random.nextFloat() < dropout) {
                continue;
            }
            float progress = step / (float) length;
            float envelope = Math.min(1.0f, Math.min(progress, 1.0f - progress) / 0.15f);
            float alpha = peak * envelope * (0.75f + random.nextFloat() * 0.5f);

            int px = Mth.floor(x);
            float fraction = x - px;
            canvas.blend(px, startY + step, argb, alpha * (1.0f - fraction));
            canvas.blend(px + 1, startY + step, argb, alpha * fraction);
        }
    }

    private void drawDust(ScratchCanvas canvas, RandomSource random, float chance) {
        int clumps = 1 + Math.round(chance * 3.0f);
        for (int i = 0; i < clumps; i++) {
            int centreX = random.nextInt(canvas.width());
            int centreY = random.nextInt(canvas.height());
            int specks = 3 + random.nextInt(6);
            int argb = random.nextInt(3) == 0 ? SCRATCH_ARGB : GRIME_ARGB;
            for (int s = 0; s < specks; s++) {
                int offsetX = random.nextInt(5) - 2;
                int offsetY = random.nextInt(5) - 2;
                float falloff = 1.0f - (Math.abs(offsetX) + Math.abs(offsetY)) / 6.0f;
                canvas.blend(centreX + offsetX, centreY + offsetY, argb,
                        (0.2f + random.nextFloat() * 0.5f) * falloff);
            }
        }
        int hairs = Math.round(chance * 2.0f);
        for (int i = 0; i < hairs; i++) {
            drawHair(canvas, random);
        }
    }

    private void drawHair(ScratchCanvas canvas, RandomSource random) {
        float x = random.nextFloat() * canvas.width();
        float y = random.nextFloat() * canvas.height();
        float angle = random.nextFloat() * Mth.TWO_PI;
        int length = 3 + random.nextInt(8);
        float alpha = 0.25f + random.nextFloat() * 0.35f;
        for (int step = 0; step < length; step++) {
            angle += (random.nextFloat() - 0.5f) * 0.8f;
            x += Mth.cos(angle);
            y += Mth.sin(angle);
            canvas.blend(Mth.floor(x), Mth.floor(y), GRIME_ARGB, alpha);
        }
    }
}
