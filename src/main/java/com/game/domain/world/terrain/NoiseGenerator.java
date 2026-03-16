package com.game.domain.world.terrain;

/**
 * Strategy interface for 2D noise generation.
 * <p>
 * Implementations can provide value noise, Perlin, OpenSimplex, etc.
 * The terrain system depends on this abstraction, making the noise
 * algorithm swappable without touching generation or rendering code.
 */
public interface NoiseGenerator {

    /**
     * Returns a noise value in the range [0.0, 1.0] for the given coordinates.
     *
     * @param x X coordinate (already scaled by the caller)
     * @param y Y coordinate (already scaled by the caller)
     * @return noise value between 0.0 and 1.0
     */
    double noise2D(double x, double y);
}
