package com.game.domain.core;

/**
 * Immutable snapshot of player control inputs for a single physics frame.
 * <p>
 * Values are clamped to valid ranges on construction.
 *
 * @param throttle  engine power request, -1 (full reverse) to 1 (full forward)
 * @param brake     brake pressure, 0 (none) to 1 (full)
 * @param steering  steering angle request, -1 (full left) to 1 (full right)
 */
public record ControlInput(double throttle, double brake, double steering) {

    public static final ControlInput NONE = new ControlInput(0, 0, 0);

    public ControlInput {
        throttle = clamp(throttle, -1.0, 1.0);
        brake = clamp(brake, 0.0, 1.0);
        steering = clamp(steering, -1.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
