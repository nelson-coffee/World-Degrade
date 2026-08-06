package dev.ncn.worlddegrade.compat.exposure;

import io.github.mortuusars.exposure.util.ExtraData;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

final class ColourAge {
    static final ExtraData.Type<Float> FADE = ExtraData.Type.floatVal("worlddegrade:fade");
    static final ExtraData.Type<Float> FLAT = ExtraData.Type.floatVal("worlddegrade:flat");
    static final ExtraData.Type<Float> CAST_STRENGTH = ExtraData.Type.floatVal("worlddegrade:cast_strength");
    static final ExtraData.Type<Integer> CAST = ExtraData.Type.intVal("worlddegrade:cast");

    static final float FADE_CAP = 0.60f;
    static final float FLAT_CAP = 0.40f;
    static final float CAST_CAP = 1.00f;

    private static final float[][] CASTS = {
            {1.10f, 0.94f, 1.02f},
            {1.10f, 1.00f, 0.86f},
            {0.93f, 1.06f, 0.96f},
            {0.94f, 0.98f, 1.10f},
    };

    private ColourAge() {
    }

    static int rollCast(RandomSource random) {
        return random.nextInt(CASTS.length);
    }

    static float[] castFactors(int index, float strength) {
        float[] full = CASTS[Math.floorMod(index, CASTS.length)];
        return new float[]{
                Mth.lerp(strength, 1.0f, full[0]),
                Mth.lerp(strength, 1.0f, full[1]),
                Mth.lerp(strength, 1.0f, full[2])};
    }

    static float step(float current, float increment, float cap) {
        float target = Math.min(cap, current + increment);
        if (target <= current || current >= 1.0f) {
            return 0.0f;
        }
        return (target - current) / (1.0f - current);
    }

    static float advance(float current, float increment, float cap) {
        return Math.min(cap, current + increment);
    }
}
