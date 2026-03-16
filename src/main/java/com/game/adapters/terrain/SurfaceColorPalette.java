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
            case TARMAC -> 0x555555;
            case DIRT -> 0x6B5428;
            case MUD -> 0x3D2817;
            case ICE -> 0xA0D0E0;
            case GRAVEL -> 0x8A8070;
            case SAND -> 0xC2B280;
        };
    }
}
