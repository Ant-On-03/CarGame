package com.game.domain.world.terrain;

/**
 * Strategy interface for terrain elevation computation.
 * <p>
 * Implementations define the height profile of the world. The physics
 * engine can query elevation for slope forces, and the renderer uses
 * it to assign pixels to parallax layers and apply height-based shading.
 */
public interface ElevationFunction {

    /**
     * Returns the elevation in arbitrary units at the given world position.
     * Typical range is roughly [-100, +100], but implementations may vary.
     *
     * @param worldX X position in metres
     * @param worldY Y position in metres
     * @return elevation value
     */
    double getElevation(double worldX, double worldY);
}
