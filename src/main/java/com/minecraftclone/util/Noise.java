package com.minecraftclone.util;

import java.util.Random;

/**
 * Classic Perlin/Simplex-style gradient noise implemented from scratch
 * (Ken Perlin's improved noise reference algorithm), plus a fractal
 * Brownian motion (fBm) helper used for terrain height maps.
 */
public final class Noise {

    private final int[] permutation = new int[512];

    public Noise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        Random rnd = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int idx = rnd.nextInt(i + 1);
            int tmp = p[i];
            p[i] = p[idx];
            p[idx] = tmp;
        }
        for (int i = 0; i < 512; i++) {
            permutation[i] = p[i & 255];
        }
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /** 3D Perlin noise in range roughly [-1, 1]. */
    public double noise3(double x, double y, double z) {
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;
        int Z = (int) Math.floor(z) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);
        double u = fade(x);
        double v = fade(y);
        double w = fade(z);

        int A = permutation[X] + Y, AA = permutation[A] + Z, AB = permutation[A + 1] + Z;
        int B = permutation[X + 1] + Y, BA = permutation[B] + Z, BB = permutation[B + 1] + Z;

        return lerp(w,
                lerp(v,
                        lerp(u, grad(permutation[AA], x, y, z), grad(permutation[BA], x - 1, y, z)),
                        lerp(u, grad(permutation[AB], x, y - 1, z), grad(permutation[BB], x - 1, y - 1, z))),
                lerp(v,
                        lerp(u, grad(permutation[AA + 1], x, y, z - 1), grad(permutation[BA + 1], x - 1, y, z - 1)),
                        lerp(u, grad(permutation[AB + 1], x, y - 1, z - 1), grad(permutation[BB + 1], x - 1, y - 1, z - 1))));
    }

    /** 2D convenience wrapper. */
    public double noise2(double x, double z) {
        return noise3(x, 0.0, z);
    }

    /**
     * Fractal Brownian motion: sums multiple octaves of noise for natural
     * looking terrain. Returns a value roughly in [-1, 1].
     */
    public double fbm2(double x, double z, int octaves, double persistence, double lacunarity) {
        double total = 0;
        double amplitude = 1;
        double frequency = 1;
        double maxAmplitude = 0;
        for (int i = 0; i < octaves; i++) {
            total += noise2(x * frequency, z * frequency) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return total / maxAmplitude;
    }

    /** 3D fBm: used for volumetric features (caves, ore veins) where clustering needs to vary in all three axes. */
    public double fbm3(double x, double y, double z, int octaves, double persistence, double lacunarity) {
        double total = 0;
        double amplitude = 1;
        double frequency = 1;
        double maxAmplitude = 0;
        for (int i = 0; i < octaves; i++) {
            total += noise3(x * frequency, y * frequency, z * frequency) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return total / maxAmplitude;
    }
}
