package com.game.ports;

/**
 * Read-only rendering constants that the renderer needs for terrain layout.
 * <p>
 * Keeps the renderer decoupled from domain configuration details —
 * it only knows about the values it actually needs to position and
 * scale chunk images on screen.
 *
 * @param chunkSizeMetres     side length of one terrain chunk in world metres
 * @param chunkSizePixels     side length of one terrain chunk image in pixels
 * @param maxLayers           number of elevation parallax layers
 * @param parallaxScaleFactor per-layer parallax scaling increment
 */
public record TerrainRenderConfig(
        double chunkSizeMetres,
        int chunkSizePixels,
        int maxLayers,
        double parallaxScaleFactor) {
}
