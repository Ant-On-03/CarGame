package com.game.domain.world.terrain;

/**
 * Immutable configuration for procedural terrain generation.
 * <p>
 * Centralises all terrain constants. Passed to all terrain components
 * so configuration lives in one place.
 *
 * @param chunkSizeMetres      side length of one terrain chunk in world metres
 * @param cellsPerChunk        number of physics cells per chunk side
 * @param chunkImageResolution pixel resolution of rendered chunk images (may
 *                             differ from screen size)
 * @param biomeFrequency       noise frequency for biome classification
 * @param roadFrequency        noise frequency for road placement
 * @param roadThreshold        noise threshold above which surface is TARMAC
 * @param elevationLevels      number of discrete elevation levels for
 *                             color/shading precision
 * @param parallaxBands        number of drawable parallax layers the renderer
 *                             iterates
 * @param elevationFrequency   noise frequency for elevation (unused by current
 *                             ElevationFunction)
 * @param parallaxScaleFactor  per-band parallax scaling increment
 * @param evictionRadius       chunk-grid radius outside which chunks are
 *                             evicted
 */
public record TerrainConfig(
        double chunkSizeMetres,
        int cellsPerChunk,
        int chunkImageResolution,
        double biomeFrequency,
        double roadFrequency,
        double roadThreshold,
        int elevationLevels,
        int parallaxBands,
        double elevationFrequency,
        double parallaxScaleFactor,
        int evictionRadius) {

    /** Derived: size of one cell in world metres. */
    public double cellSizeMetres() {
        return chunkSizeMetres / cellsPerChunk;
    }

    /** Derived: size of one cell in chunk image pixels. */
    public int cellSizePixels() {
        return chunkImageResolution / cellsPerChunk;
    }

    /** Derived: pixels per metre for chunk image rendering. */
    public double pixelsPerMetre() {
        return (double) chunkImageResolution / chunkSizeMetres;
    }

    /**
     * Optimised configuration: 200 elevation levels mapped into 24
     * parallax bands at 256×256 image resolution.
     */
    public static TerrainConfig defaults() {
        return new TerrainConfig(
                64.0, // chunkSizeMetres
                32, // cellsPerChunk
                256, // chunkImageResolution (half-res, upscaled by renderer)
                0.005, // biomeFrequency
                0.01, // roadFrequency
                0.8, // roadThreshold
                200, // elevationLevels (color/shading precision)
                24, // parallaxBands (actual drawable layers)
                0.008, // elevationFrequency
                0.04, // parallaxScaleFactor (adjusted for 24 bands)
                3 // evictionRadius
        );
    }
}
