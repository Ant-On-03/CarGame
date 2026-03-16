package com.game.domain.world;

/**
 * Smooth follow camera that tracks a target position using exponential lerp,
 * with velocity-based look-ahead so the view shifts ahead of the car at speed.
 * <p>
 * Pure domain class — no AWT/Swing imports. All values in SI units (metres).
 * The camera position converges towards the target at a rate controlled by
 * {@link #smoothing}. Higher smoothing means faster convergence.
 * <p>
 * Look-ahead: at speed the camera target is offset in the direction of travel.
 * This gives the player more visibility of what's coming. The offset is
 * proportional to speed and capped at {@link #maxLookAhead} metres.
 */
public class Camera {

    private static final double DEFAULT_SMOOTHING = 6.0;

    /** Maximum look-ahead offset in metres. */
    private static final double DEFAULT_MAX_LOOK_AHEAD = 8.0;

    /** Speed (m/s) at which full look-ahead is reached. */
    private static final double LOOK_AHEAD_FULL_SPEED = 40.0;

    private double x;
    private double y;
    private final double smoothing;
    private final double maxLookAhead;

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
        this.maxLookAhead = DEFAULT_MAX_LOOK_AHEAD;
    }

    /**
     * Creates a camera with default smoothing ({@value #DEFAULT_SMOOTHING}).
     */
    public Camera(double startX, double startY) {
        this(startX, startY, DEFAULT_SMOOTHING);
    }

    /**
     * Updates the camera position to follow the target using exponential lerp,
     * with an offset in the direction of the car's velocity (look-ahead).
     * <p>
     * Formula: {@code pos += (target - pos) * (1 - e^(-smoothing * dt))}
     * <p>
     * The look-ahead scales linearly with speed up to {@link #maxLookAhead},
     * reaching full offset at {@link #LOOK_AHEAD_FULL_SPEED} m/s (~144 km/h).
     *
     * @param targetX target world X (metres)
     * @param targetY target world Y (metres)
     * @param velX    car velocity X component (m/s)
     * @param velY    car velocity Y component (m/s)
     * @param dt      time step (seconds)
     */
    public void update(double targetX, double targetY, double velX, double velY, double dt) {
        // Compute look-ahead offset from velocity
        double speed = Math.sqrt(velX * velX + velY * velY);
        double lookAheadFactor = Math.min(speed / LOOK_AHEAD_FULL_SPEED, 1.0);
        double lookAheadDist = maxLookAhead * lookAheadFactor;

        double aheadX = 0.0;
        double aheadY = 0.0;
        if (speed > 0.5) {
            // Normalise velocity direction and scale by look-ahead distance
            aheadX = (velX / speed) * lookAheadDist;
            aheadY = (velY / speed) * lookAheadDist;
        }

        double effectiveTargetX = targetX + aheadX;
        double effectiveTargetY = targetY + aheadY;

        double factor = 1.0 - Math.exp(-smoothing * dt);
        x += (effectiveTargetX - x) * factor;
        y += (effectiveTargetY - y) * factor;
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
