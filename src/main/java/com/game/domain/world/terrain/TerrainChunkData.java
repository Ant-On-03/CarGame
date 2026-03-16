package com.game.domain.world.terrain;

import com.game.domain.SurfaceType;

/**
 * Physics-relevant terrain data for a single chunk.
 * <p>
 * Contains only domain data — no rendering types (no {@code BufferedImage}).
 * The renderer builds its own visual representation from this data.
 *
 * @param chunkX        chunk grid X coordinate
 * @param chunkY        chunk grid Y coordinate
 * @param cells         flat array of surface types, row-major, length =
 *                      cellsPerChunk²
 * @param elevationGrid flat array of per-cell elevation values, same layout as
 *                      cells
 */
public record TerrainChunkData(
        int chunkX,
        int chunkY,
        SurfaceType[] cells,
        double[] elevationGrid) {
}
