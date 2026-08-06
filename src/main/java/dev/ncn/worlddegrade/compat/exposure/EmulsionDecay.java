package dev.ncn.worlddegrade.compat.exposure;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

final class EmulsionDecay {
    private static final int BLEACH_ARGB = 0xFFE6D9BE;
    private static final int STAIN_ARGB = 0xFF6B4A2A;
    private static final int TIDE_ARGB = 0xFF4A3018;

    private EmulsionDecay() {
    }

    static void apply(ScratchCanvas canvas, RandomSource random, float chance) {
        int blotches = 1 + Math.round(chance * 2.0f);
        for (int i = 0; i < blotches; i++) {
            drawBlotch(canvas, random, chance);
        }
        drawEdgeDecay(canvas, random, chance);
        int stains = Math.round(chance * 2.0f);
        for (int i = 0; i < stains; i++) {
            drawWaterStain(canvas, random, chance);
        }
    }

    private static float edgeAt(float angle, float radius, float phaseA, float phaseB) {
        return radius * (0.70f
                + 0.16f * Mth.sin(angle * 3.0f + phaseA)
                + 0.09f * Mth.sin(angle * 5.0f + phaseB)
                + 0.05f * Mth.sin(angle * 11.0f + phaseA * 2.0f)
                + 0.03f * Mth.sin(angle * 17.0f + phaseB * 2.0f));
    }

    private static void drawBlotch(ScratchCanvas canvas, RandomSource random, float chance) {
        int frame = Math.min(canvas.width(), canvas.height());
        float radius = frame * (0.07f + random.nextFloat() * 0.06f);
        int centreX = random.nextInt(canvas.width());
        int centreY = random.nextInt(canvas.height());
        int argb = random.nextBoolean() ? BLEACH_ARGB : STAIN_ARGB;
        float peak = 0.5f + random.nextFloat() * 0.35f;
        float phaseA = random.nextFloat() * Mth.TWO_PI;
        float phaseB = random.nextFloat() * Mth.TWO_PI;

        int reach = Mth.ceil(radius);
        for (int dy = -reach; dy <= reach; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                float distance = Mth.sqrt(dx * dx + dy * dy);
                float edge = edgeAt((float) Math.atan2(dy, dx), radius, phaseA, phaseB);
                if (distance > edge + (random.nextFloat() - 0.5f) * 1.6f) {
                    continue;
                }
                float falloff = Math.min(1.0f, (edge - distance) / 2.0f);
                float mottle = 0.55f + 0.45f * (0.5f + 0.5f
                        * Mth.sin(dx * 0.7f + phaseA) * Mth.cos(dy * 0.6f + phaseB));
                float rim = (edge - distance) < 2.0f ? 1.35f : 1.0f;
                canvas.blend(centreX + dx, centreY + dy, argb,
                        Math.min(1.0f, peak * falloff * mottle * rim));
            }
        }
    }

    private static void drawEdgeDecay(ScratchCanvas canvas, RandomSource random, float chance) {
        int frame = Math.min(canvas.width(), canvas.height());
        float maxDepth = frame * (0.04f + chance * 0.08f);
        int edges = 1 + random.nextInt(3);
        for (int i = 0; i < edges; i++) {
            int side = random.nextInt(4);
            float phaseA = random.nextFloat() * Mth.TWO_PI;
            float phaseB = random.nextFloat() * Mth.TWO_PI;
            float scaleA = 0.05f + random.nextFloat() * 0.08f;
            float peak = 0.45f + random.nextFloat() * 0.3f;
            boolean vertical = side < 2;
            int along = vertical ? canvas.height() : canvas.width();

            for (int t = 0; t < along; t++) {
                float wave = 0.5f + 0.3f * Mth.sin(t * scaleA + phaseA)
                        + 0.2f * Mth.sin(t * scaleA * 2.7f + phaseB);
                int depth = Math.round(maxDepth * Mth.clamp(wave, 0.0f, 1.0f));
                for (int d = 0; d < depth; d++) {
                    float alpha = peak * (1.0f - d / (float) Math.max(1, depth));
                    switch (side) {
                        case 0 -> canvas.blend(d, t, STAIN_ARGB, alpha);
                        case 1 -> canvas.blend(canvas.width() - 1 - d, t, STAIN_ARGB, alpha);
                        case 2 -> canvas.blend(t, d, STAIN_ARGB, alpha);
                        default -> canvas.blend(t, canvas.height() - 1 - d, STAIN_ARGB, alpha);
                    }
                }
            }
        }
    }

    private static void drawWaterStain(ScratchCanvas canvas, RandomSource random, float chance) {
        int frame = Math.min(canvas.width(), canvas.height());
        float radius = frame * (0.14f + random.nextFloat() * 0.10f);
        int centreX = random.nextInt(canvas.width());
        int centreY = random.nextInt(canvas.height());
        float phaseA = random.nextFloat() * Mth.TWO_PI;
        float phaseB = random.nextFloat() * Mth.TWO_PI;
        float wash = 0.10f + random.nextFloat() * 0.10f;
        float rim = 0.28f + random.nextFloat() * 0.22f;

        int reach = Mth.ceil(radius);
        for (int dy = -reach; dy <= reach; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                float distance = Mth.sqrt(dx * dx + dy * dy);
                float angle = (float) Math.atan2(dy, dx);
                float edge = edgeAt(angle, radius, phaseA, phaseB);
                if (distance > edge) {
                    continue;
                }
                float thickness = 1.1f + 0.9f * (0.5f + 0.5f * Mth.sin(angle * 7.0f + phaseA));
                if (edge - distance < thickness) {
                    canvas.blend(centreX + dx, centreY + dy, TIDE_ARGB,
                            rim * (0.6f + 0.4f * (0.5f + 0.5f * Mth.sin(angle * 4.0f + phaseB))));
                } else {
                    canvas.blend(centreX + dx, centreY + dy, STAIN_ARGB, wash);
                }
            }
        }
    }
}
