package com.game.adapters.terrain;

import com.game.domain.SurfaceType;
import com.game.domain.world.terrain.ElevationFunction;
import com.game.domain.world.terrain.SurfaceClassifier;
import com.game.domain.world.terrain.TerrainChunkData;
import com.game.domain.world.terrain.TerrainConfig;
import com.game.ports.TerrainProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core terrain chunk orchestrator — implements the physics-facing
 * {@link TerrainProvider} port.
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Chunk lifecycle (lazy creation, concurrent cache, eviction)</li>
 * <li>Delegating surface classification to {@link SurfaceClassifier}</li>
 * <li>Delegating elevation queries to {@link ElevationFunction}</li>
 * <li>Building {@link TerrainChunkData} (domain-only, no images)</li>
 * </ul>
 * <p>
 * No rendering / AWT types pass through this class.
 */
public final class TerrainChunkGenerator implements TerrainProvider {

    private final SurfaceClassifier surfaceClassifier;
    private final ElevationFunction elevationFunction;
    private final TerrainConfig config;
    private final Map<String, TerrainChunkData> chunkCache = new ConcurrentHashMap<>();

    public TerrainChunkGenerator(SurfaceClassifier surfaceClassifier,
            ElevationFunction elevationFunction,
            TerrainConfig config) {
        this.surfaceClassifier = surfaceClassifier;
        this.elevationFunction = elevationFunction;
        this.config = config;
    }

    // ================================================================
    // TerrainProvider port (physics-facing)
    // ================================================================

    @Override
    public SurfaceType getSurfaceAt(double worldX, double worldY) {
        int chunkX = (int) Math.floor(worldX / config.chunkSizeMetres());
        int chunkY = (int) Math.floor(worldY / config.chunkSizeMetres());
        TerrainChunkData chunk = getOrCreateChunk(chunkX, chunkY);

        double localX = worldX - (chunkX * config.chunkSizeMetres());
        double localY = worldY - (chunkY * config.chunkSizeMetres());

        int cellX = clamp((int) (localX / config.cellSizeMetres()), 0, config.cellsPerChunk() - 1);
        int cellY = clamp((int) (localY / config.cellSizeMetres()), 0, config.cellsPerChunk() - 1);

        return chunk.cells()[cellY * config.cellsPerChunk() + cellX];
    }

    @Override
    public double getElevationAt(double worldX, double worldY) {
        return elevationFunction.getElevation(worldX, worldY);
    }

    // ================================================================
    // Chunk data access (used by TerrainImageRenderer)
    // ================================================================

    /**
     * Returns the domain-only chunk data for the given chunk coordinates.
     * Creates the chunk lazily if it doesn't exist yet.
     */
    public TerrainChunkData getOrCreateChunk(int chunkX, int chunkY) {
        String key = chunkX + "," + chunkY;
        return chunkCache.computeIfAbsent(key, k -> generateChunk(chunkX, chunkY));
    }

    /**
     * Evicts chunks that are further than the configured radius from the camera.
     */
    public void evictDistantChunks(double camX, double camY) {
        int camChunkX = (int) Math.floor(camX / config.chunkSizeMetres());
        int camChunkY = (int) Math.floor(camY / config.chunkSizeMetres());
        int radius = config.evictionRadius();

        chunkCache.keySet().removeIf(key -> {
            String[] parts = key.split(",");
            int cx = Integer.parseInt(parts[0]);
            int cy = Integer.parseInt(parts[1]);
            return Math.abs(cx - camChunkX) > radius || Math.abs(cy - camChunkY) > radius;
        });
    }

    public TerrainConfig getConfig() {
        return config;
    }

    public ElevationFunction getElevationFunction() {
        return elevationFunction;
    }

    // ================================================================
    // Chunk generation
    // ================================================================

    private TerrainChunkData generateChunk(int chunkX, int chunkY) {
        int cells = config.cellsPerChunk();
        SurfaceType[] surfaceCells = new SurfaceType[cells * cells];
        double[] elevationGrid = new double[cells * cells];

        double chunkWorldX = chunkX * config.chunkSizeMetres();
        double chunkWorldY = chunkY * config.chunkSizeMetres();

        for (int cy = 0; cy < cells; cy++) {
            for (int cx = 0; cx < cells; cx++) {
                double cellWorldX = chunkWorldX + (cx + 0.5) * config.cellSizeMetres();
                double cellWorldY = chunkWorldY + (cy + 0.5) * config.cellSizeMetres();
                int idx = cy * cells + cx;

                surfaceCells[idx] = surfaceClassifier.classify(cellWorldX, cellWorldY);
                elevationGrid[idx] = elevationFunction.getElevation(cellWorldX, cellWorldY);
            }
        }

        return new TerrainChunkData(chunkX, chunkY, surfaceCells, elevationGrid);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
