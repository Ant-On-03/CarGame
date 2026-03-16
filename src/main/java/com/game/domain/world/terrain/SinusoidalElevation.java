package com.game.domain.world.terrain;

/**
 * Sinusoidal ridge elevation that produces rolling hills.
 * <p>
 * Combines sine and cosine waves at different frequencies to create
 * a visually interesting landscape of ridges and valleys.
 * Range is approximately [-80, +80].
 */
public final class SinusoidalElevation implements ElevationFunction {

    @Override
    public double getElevation(double worldX, double worldY) {
        return Math.sin(worldX * 0.03) * 50.0
                + Math.cos(worldY * 0.02) * 30.0;
    }
}
