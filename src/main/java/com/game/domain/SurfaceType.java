package com.game.domain;

/**
 * Terrain surface types that affect tire friction and rolling resistance.
 * <p>
 * Each surface has a {@link #frictionMultiplier} (applied to the tire's grip
 * force limit) and an {@link #extraRollingResistance} (added to the car's
 * base rolling resistance, in Newtons).
 * <p>
 * Pure domain enum — no AWT/Swing imports.
 *
 * @see com.game.ports.TerrainProvider
 */
public enum SurfaceType {

    /** Smooth tarmac road — full grip, no extra resistance. */
    TARMAC(1.0, 0.0),

    /** Loose dirt — moderate grip loss, some extra drag. */
    DIRT(0.55, 30.0),

    /** Gravel — slight grip loss, moderate drag from stones. */
    GRAVEL(0.65, 20.0),

    /** Wet mud — severe grip loss, heavy drag. */
    MUD(0.35, 80.0),

    /** Ice — almost no grip, no extra resistance. */
    ICE(0.12, 0.0),

    /** Dry sand — poor grip, very heavy drag. */
    SAND(0.45, 100.0);

    private final double frictionMultiplier;
    private final double extraRollingResistance;

    SurfaceType(double frictionMultiplier, double extraRollingResistance) {
        this.frictionMultiplier = frictionMultiplier;
        this.extraRollingResistance = extraRollingResistance;
    }

    /**
     * Multiplier applied to the tire's friction circle (0.0 - 1.0).
     * A value of 1.0 means full grip; lower values reduce maximum tire force.
     */
    public double frictionMultiplier() {
        return frictionMultiplier;
    }

    /**
     * Additional rolling resistance force in Newtons.
     * Simulates the extra drag from soft/loose surfaces.
     */
    public double extraRollingResistance() {
        return extraRollingResistance;
    }
}
