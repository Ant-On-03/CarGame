package com.game.domain.world.terrain;

import java.util.Random;

/**
 * Seed-based value noise implementation using a 512-element permutation table.
 * <p>
 * Produces smooth, deterministic noise suitable for terrain biomes and roads.
 * Extracted from the former monolithic {@code ProceduralTerrainGenerator}.
 */
public final class ValueNoiseGenerator implements NoiseGenerator {

    private final int[] perm;

    public ValueNoiseGenerator(long seed) {
        this.perm = buildPermutationTable(seed);
    }

    @Override
    public double noise2D(double x, double y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);

        double tx = x - xi;
        double ty = y - yi;

        int rx0 = xi & 255;
        int rx1 = (rx0 + 1) & 255;
        int ry0 = yi & 255;
        int ry1 = (ry0 + 1) & 255;

        double c00 = perm[perm[rx0] + ry0] / 255.0;
        double c10 = perm[perm[rx1] + ry0] / 255.0;
        double c01 = perm[perm[rx0] + ry1] / 255.0;
        double c11 = perm[perm[rx1] + ry1] / 255.0;

        double sx = smoothstep(tx);
        double sy = smoothstep(ty);

        double nx0 = lerp(c00, c10, sx);
        double nx1 = lerp(c01, c11, sx);
        return lerp(nx0, nx1, sy);
    }

    private static int[] buildPermutationTable(long seed) {
        int[] p = new int[512];
        Random rnd = new Random(seed);
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        for (int i = 0; i < 256; i++) {
            int swap = rnd.nextInt(256);
            int temp = p[i];
            p[i] = p[swap];
            p[swap] = temp;
        }
        for (int i = 0; i < 256; i++) {
            p[i + 256] = p[i];
        }
        return p;
    }

    private static double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }
}
