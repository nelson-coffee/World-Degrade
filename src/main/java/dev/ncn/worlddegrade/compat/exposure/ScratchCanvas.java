package dev.ncn.worlddegrade.compat.exposure;

import io.github.mortuusars.exposure.data.ColorPalette;
import io.github.mortuusars.exposure.util.color.Color;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;

class ScratchCanvas {
    private static final int[] BAYER = {
            0, 8, 2, 10,
            12, 4, 14, 6,
            3, 11, 1, 9,
            15, 7, 13, 5};
    private static final int DITHER_STRENGTH = 6;

    private final byte[] pixels;
    private final int width;
    private final int height;
    private final ColorPalette palette;

    private final Map<Integer, Byte> nearestCache = new HashMap<>();

    private byte[] lighter;
    private byte[] darker;

    ScratchCanvas(byte[] pixels, int width, int height, ColorPalette palette) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.palette = palette;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    void blend(int x, int y, int argb, float alpha) {
        if (x < 0 || x >= width || y < 0 || y >= height || alpha <= 0.0f) {
            return;
        }
        int index = y * width + x;
        int base = palette.byId(pixels[index] & 0xFF);
        float strength = Math.min(alpha, 1.0f);

        int dither = (BAYER[(y & 3) * 4 + (x & 3)] - 8) * DITHER_STRENGTH / 8;
        int r = channel(base, 16, argb, strength, dither);
        int g = channel(base, 8, argb, strength, dither);
        int b = channel(base, 0, argb, strength, dither);

        pixels[index] = nearest((r << 16) | (g << 8) | b);
    }

    private static int channel(int base, int shift, int target, float strength, int dither) {
        int from = (base >> shift) & 0xFF;
        int to = (target >> shift) & 0xFF;
        return Mth.clamp(Math.round(from + (to - from) * strength) + dither, 0, 255);
    }

    private byte nearest(int rgb) {
        Byte cached = nearestCache.get(rgb);
        if (cached != null) {
            return cached;
        }
        byte index = (byte) palette.closestTo(Color.argb(0xFF000000 | rgb));
        nearestCache.put(rgb, index);
        return index;
    }

    private static final int FADE_BUCKETS = 6;
    private static final float MILKY_MIDPOINT = 138.0f;

    void ageColour(RandomSource random, float fade, float flat, float[] cast) {
        if (fade <= 0.0f && flat <= 0.0f) {
            return;
        }
        byte[][] remaps = new byte[FADE_BUCKETS][];
        for (int bucket = 0; bucket < FADE_BUCKETS; bucket++) {
            float scale = 0.55f + 0.45f * (bucket / (float) (FADE_BUCKETS - 1));
            remaps[bucket] = buildAgeRemap(fade * scale, flat * scale, cast);
        }

        float phaseA = random.nextFloat() * Mth.TWO_PI;
        float phaseB = random.nextFloat() * Mth.TWO_PI;
        float phaseC = random.nextFloat() * Mth.TWO_PI;
        float phaseD = random.nextFloat() * Mth.TWO_PI;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float broad = Mth.sin(x * 0.08f + phaseA) * Mth.cos(y * 0.07f + phaseB);
                float fine = Mth.sin(x * 0.21f + phaseC) * Mth.cos(y * 0.19f + phaseD);
                float blend = Mth.clamp(0.5f + 0.36f * broad + 0.14f * fine, 0.0f, 1.0f);
                float jitter = (BAYER[(y & 3) * 4 + (x & 3)] - 8) / 16.0f;
                int bucket = Mth.clamp(Math.round(blend * (FADE_BUCKETS - 1) + jitter),
                        0, FADE_BUCKETS - 1);
                int index = y * width + x;
                pixels[index] = remaps[bucket][pixels[index] & 0xFF];
            }
        }
    }

    private byte[] buildAgeRemap(float fade, float flat, float[] cast) {
        byte[] remap = new byte[256];
        for (int i = 0; i < 256; i++) {
            int argb = palette.byId(i);
            float r = (argb >> 16) & 0xFF;
            float g = (argb >> 8) & 0xFF;
            float b = argb & 0xFF;

            float luma = 0.299f * r + 0.587f * g + 0.114f * b;
            r = Mth.lerp(fade, r, luma);
            g = Mth.lerp(fade, g, luma);
            b = Mth.lerp(fade, b, luma);

            r = MILKY_MIDPOINT + (r - MILKY_MIDPOINT) * (1.0f - flat);
            g = MILKY_MIDPOINT + (g - MILKY_MIDPOINT) * (1.0f - flat);
            b = MILKY_MIDPOINT + (b - MILKY_MIDPOINT) * (1.0f - flat);

            remap[i] = nearest((Mth.clamp(Math.round(r * cast[0]), 0, 255) << 16)
                    | (Mth.clamp(Math.round(g * cast[1]), 0, 255) << 8)
                    | Mth.clamp(Math.round(b * cast[2]), 0, 255));
        }
        return remap;
    }

    void grain(RandomSource random, float amount) {
        if (amount <= 0.0f) {
            return;
        }
        buildGrainTable();
        for (int i = 0; i < pixels.length; i++) {
            if (random.nextFloat() >= amount) {
                continue;
            }
            int current = pixels[i] & 0xFF;
            pixels[i] = random.nextBoolean() ? lighter[current] : darker[current];
        }
    }

    private void buildGrainTable() {
        if (lighter != null) {
            return;
        }
        lighter = new byte[256];
        darker = new byte[256];
        for (int i = 0; i < 256; i++) {
            int argb = palette.byId(i);
            lighter[i] = (byte) palette.closestTo(Color.argb(scale(argb, 1.08f)));
            darker[i] = (byte) palette.closestTo(Color.argb(scale(argb, 0.92f)));
        }
    }

    private static int scale(int argb, float factor) {
        int r = Mth.clamp(Math.round(((argb >> 16) & 0xFF) * factor), 0, 255);
        int g = Mth.clamp(Math.round(((argb >> 8) & 0xFF) * factor), 0, 255);
        int b = Mth.clamp(Math.round((argb & 0xFF) * factor), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
