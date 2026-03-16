package com.game.ports;

import com.game.domain.SurfaceType;

/**
 * Port for querying the terrain surface at a given world position.
 * <p>
 * The physics engine uses this to look up the surface type under each
 * wheel, which then affects tire friction and rolling resistance.
 * <p>
 * Implementations may be procedural (noise-based), loaded from a map,
 * or a simple constant (e.g. always TARMAC for headless simulation).
 */
public interface TerrainProvider {

    /**
     * Returns the surface type at the given world-space position.
     *
     * @param worldX X position in metres
     * @param worldY Y position in metres
     * @return the surface type at that location; never null
     */
    SurfaceType getSurfaceAt(double worldX, double worldY);

    /**
     * Returns the terrain elevation (arbitrary units) at the given world position.
     * <p>
     * Prepared for future physics use (slope forces, grade resistance).
     * Current physics code does not call this method yet.
     *
     * @param worldX X position in metres
     * @param worldY Y position in metres
     * @return elevation value at that location
     */
    double getElevationAt(double worldX, double worldY);
}
