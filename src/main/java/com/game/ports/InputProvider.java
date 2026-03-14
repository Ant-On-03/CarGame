package com.game.ports;

/**
 * Port for reading player input.
 * <p>
 * Adapters translate device-specific events (keyboard, gamepad, etc.)
 * into this device-agnostic contract so the domain never depends on
 * any UI framework.
 */
public interface InputProvider {

    /**
     * Returns the throttle value.
     * Range: 0 (no throttle) to 1 (full throttle) for keyboard;
     * gamepads may support -1 (reverse) to 1 (full forward).
     */
    double getThrottle();

    /**
     * Returns the brake pressure.
     * Range: 0 (no brake) to 1 (full brake).
     */
    double getBrake();

    /**
     * Returns the steering value.
     * Range: -1 (full left) to 1 (full right), 0 is neutral.
     */
    double getSteering();
}
