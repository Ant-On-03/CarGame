package com.game.domain;

/**
 * Smooth follow camera that tracks a target position using exponential lerp.
 * <p>
 * Pure domain class — no AWT/Swing imports. All values in SI units (metres).
 * The camera position converges towards the target at a rate controlled by
 * {@link #smoothing}. Higher smoothing means faster convergence.
 * <p>
 * The smoothing constant is tuned so the camera cannot be outrun at top speed
 * (exponential lerp guarantees this mathematically, but a minimum smoothing
 * of ~5.0 keeps the car comfortably centred even at 200+ km/h).
 */
public class Camera {

    private static final double DEFAULT_SMOOTHING = 6.0;

    private double x;
    private double y;
    private final double smoothing;

    /**
     * Creates a camera at the given initial world position.
     *
     * @param startX    initial X in metres
     * @param startY    initial Y in metres
     * @param smoothing exponential lerp factor (higher = faster follow)
     */
    public Camera(double startX, double startY, double smoothing) {
        this.x = startX;
        this.y = startY;
        this.smoothing = smoothing;
    }

    /**
     * Creates a camera with default smoothing ({@value #DEFAULT_SMOOTHING}).
     */
    public Camera(double startX, double startY) {
        this(startX, startY, DEFAULT_SMOOTHING);
    }

    /**
     * Updates the camera position to follow the target using exponential lerp.
     * <p>
     * Formula: {@code pos += (target - pos) * (1 - e^(-smoothing * dt))}
     * <p>
     * This is frame-rate independent — the same visual result regardless of
     * whether the physics runs at 60 or 120 Hz.
     *
     * @param targetX target world X (metres)
     * @param targetY target world Y (metres)
     * @param dt      time step (seconds)
     */
    public void update(double targetX, double targetY, double dt) {
        double factor = 1.0 - Math.exp(-smoothing * dt);
        x += (targetX - x) * factor;
        y += (targetY - y) * factor;
    }

    /** Camera world X in metres. */
    public double getX() {
        return x;
    }

    /** Camera world Y in metres. */
    public double getY() {
        return y;
    }
}
