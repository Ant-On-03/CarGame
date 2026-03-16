package com.game.ports;

import java.awt.image.BufferedImage;

/**
 * Renderer-facing port for terrain chunk visuals.
 * <p>
 * The renderer depends on this interface (never on a concrete generator)
 * to retrieve pre-rendered chunk images. This keeps terrain generation
 * logic fully decoupled from the rendering adapter.
 */
public interface TerrainImageProvider {

    /**
     * Returns the pre-rendered elevation layer images for the given chunk.
     * <p>
     * The array length equals the number of elevation layers. Entries may
     * be {@code null} if no terrain pixels fall on that layer (lazy allocation).
     *
     * @param chunkX chunk grid X coordinate
     * @param chunkY chunk grid Y coordinate
     * @return array of layer images; never null, but entries may be null
     */
    BufferedImage[] getChunkImages(int chunkX, int chunkY);

    /**
     * Evicts cached chunks that are far from the camera to free memory.
     *
     * @param camX camera centre X in metres
     * @param camY camera centre Y in metres
     */
    void evictDistantChunks(double camX, double camY);

    /**
     * Returns the rendering configuration the renderer needs for layout.
     */
    TerrainRenderConfig getRenderConfig();
}
