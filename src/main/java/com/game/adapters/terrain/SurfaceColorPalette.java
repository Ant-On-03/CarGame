package com.game.adapters.terrain;

import com.game.domain.SurfaceType;

/**
 * Maps {@link SurfaceType} values to ARGB pixel colours for terrain rendering.
 * <p>
 * Separated from the rendering loop so colour palettes can be swapped
 * (e.g. biome themes, night mode, seasonal palettes) without touching
 * the terrain image renderer.
 */
public final class SurfaceColorPalette {

    private SurfaceColorPalette() {
    }

    /**
     * Returns the base ARGB colour (without alpha) for the given surface type.
     *
     * @param type the surface type
     * @return 24-bit RGB colour (0xRRGGBB)
     */
    public static int colorFor(SurfaceType type) {
        return switch (type) {
            case TARMAC -> 0x3E4C41; // Mossy dark asphalt
            case DIRT   -> 0x5C8038; // Vibrant, lush green grass
            case MUD    -> 0x324D22; // Deep, dark swampy green
            case ICE    -> 0x98C9A3; // Crisp, minty icy green
            case GRAVEL -> 0x6B7D67; // Earthy, greenish stone
            case SAND   -> 0x8A9A41; // Yellow-green mossy sand
        };
    }
}
