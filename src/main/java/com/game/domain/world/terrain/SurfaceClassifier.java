package com.game.domain.world.terrain;

import com.game.domain.SurfaceType;

/**
 * Classifies a world-space position into a {@link SurfaceType} using noise.
 * <p>
 * Pure domain logic with no rendering concerns. The noise generator and
 * thresholds are injected, making the classification testable and tuneable.
 */
public final class SurfaceClassifier {

    private final NoiseGenerator noise;
    private final TerrainConfig config;

    public SurfaceClassifier(NoiseGenerator noise, TerrainConfig config) {
        this.noise = noise;
        this.config = config;
    }

    /**
     * Returns the surface type at the given world-space position.
     *
     * @param worldX X position in metres
     * @param worldY Y position in metres
     * @return the classified surface type; never null
     */
    public SurfaceType classify(double worldX, double worldY) {
        double roadNoise = noise.noise2D(
                worldX * config.roadFrequency(),
                worldY * config.roadFrequency());
        if (roadNoise > config.roadThreshold()) {
            return SurfaceType.TARMAC;
        }

        double biomeNoise = noise.noise2D(
                worldX * config.biomeFrequency() + 100,
                worldY * config.biomeFrequency() + 100);
        if (biomeNoise > 0.6)
            return SurfaceType.MUD;
        if (biomeNoise < 0.3)
            return SurfaceType.ICE;
        return SurfaceType.DIRT;
    }
}
