package com.game.domain.vehicle;

import com.game.domain.core.Vector2;

/**
 * Represents one wheel of the vehicle.
 * <p>
 * The {@link #localOffset} is the wheel's position relative to the car
 * centre in car-local coordinates (X-right, Y-down). This is fixed at
 * construction and never changes.
 * <p>
 * {@link #normalForce} and {@link #slipping} are updated every physics
 * frame by the engine.
 */
public class Wheel {

    private final WheelPosition position;
    private final Vector2 localOffset;
    private final Tire tire;

    private double normalForce;
    private boolean slipping;

    public Wheel(WheelPosition position, Vector2 localOffset, Tire tire) {
        this.position = position;
        this.localOffset = localOffset;
        this.tire = tire;
        this.normalForce = 0.0;
        this.slipping = false;
    }

    public WheelPosition getPosition() {
        return position;
    }

    public Vector2 getLocalOffset() {
        return localOffset;
    }

    public Tire getTire() {
        return tire;
    }

    public double getNormalForce() {
        return normalForce;
    }

    public void setNormalForce(double normalForce) {
        this.normalForce = Math.max(0.0, normalForce);
    }

    public boolean isSlipping() {
        return slipping;
    }

    public void setSlipping(boolean slipping) {
        this.slipping = slipping;
    }

    public boolean isFront() {
        return position.isFront();
    }

    public boolean isRear() {
        return position.isRear();
    }

    /**
     * Returns {@code true} if this wheel receives engine torque
     * under the given drive type.
     */
    public boolean isDriven(DriveType driveType) {
        return switch (driveType) {
            case FWD -> isFront();
            case RWD -> isRear();
            case AWD -> true;
        };
    }
}
