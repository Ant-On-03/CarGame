package com.game.domain.vehicle;

import com.game.domain.core.Vector2;

import java.util.ArrayList;
import java.util.List;

/**
 * Core domain entity representing a vehicle in the 2D top-down world.
 * <p>
 * All values are in SI units: position in metres, velocity in m/s,
 * rotation in radians (0 = up, clockwise positive).
 * <p>
 * The car owns four {@link Wheel} instances whose layout is derived
 * from the {@link CarConfig} at construction time.
 */
public class Car {

    private Vector2 position;
    private Vector2 velocity;
    private double rotation;
    private double angularVelocity;

    private CarConfig config;
    private List<Wheel> wheels;

    public Car(Vector2 position, CarConfig config) {
        this.position = position;
        this.velocity = Vector2.ZERO;
        this.rotation = 0.0;
        this.angularVelocity = 0.0;
        this.config = config;
        this.wheels = createWheels(config);
    }

    // ---- Factory ----

    private static List<Wheel> createWheels(CarConfig config) {
        double halfTrack = config.trackWidth() / 2.0;
        double halfWheelbase = config.wheelbase() / 2.0;

        return new ArrayList<>(List.of(
                new Wheel(WheelPosition.FRONT_LEFT,
                        new Vector2(-halfTrack, -halfWheelbase), config.frontTire()),
                new Wheel(WheelPosition.FRONT_RIGHT,
                        new Vector2(halfTrack, -halfWheelbase), config.frontTire()),
                new Wheel(WheelPosition.REAR_LEFT,
                        new Vector2(-halfTrack, halfWheelbase), config.rearTire()),
                new Wheel(WheelPosition.REAR_RIGHT,
                        new Vector2(halfTrack, halfWheelbase), config.rearTire())
        ));
    }

    // ---- Derived vectors ----

    /** Unit vector pointing in the car's forward direction. */
    public Vector2 forwardVector() {
        return Vector2.fromAngle(rotation);
    }

    /** Unit vector pointing to the car's right side. */
    public Vector2 rightVector() {
        return forwardVector().perpendicular();
    }

    /** Scalar speed (magnitude of velocity). */
    public double getSpeed() {
        return velocity.magnitude();
    }

    /** Forward component of velocity (positive = moving forward). */
    public double getLongitudinalSpeed() {
        return velocity.dot(forwardVector());
    }

    /** Lateral component of velocity (positive = sliding right). */
    public double getLateralSpeed() {
        return velocity.dot(rightVector());
    }

    // ---- Accessors ----

    public Vector2 getPosition() {
        return position;
    }

    public void setPosition(Vector2 position) {
        this.position = position;
    }

    public Vector2 getVelocity() {
        return velocity;
    }

    public void setVelocity(Vector2 velocity) {
        this.velocity = velocity;
    }

    public double getRotation() {
        return rotation;
    }

    public void setRotation(double rotation) {
        this.rotation = rotation;
    }

    public double getAngularVelocity() {
        return angularVelocity;
    }

    public void setAngularVelocity(double angularVelocity) {
        this.angularVelocity = angularVelocity;
    }

    public CarConfig getConfig() {
        return config;
    }

    /**
     * Replaces the car configuration at runtime.
     * <p>
     * Wheels are re-created to reflect the new wheelbase and track width.
     * Existing velocity and angular velocity are preserved.
     */
    public void setConfig(CarConfig newConfig) {
        this.config = newConfig;
        // Rebuild wheel layout from new dimensions
        List<Wheel> newWheels = createWheels(newConfig);
        // Copy over runtime state (normal forces, slip) from old wheels
        for (int i = 0; i < Math.min(this.wheels.size(), newWheels.size()); i++) {
            newWheels.get(i).setNormalForce(this.wheels.get(i).getNormalForce());
            newWheels.get(i).setSlipping(this.wheels.get(i).isSlipping());
        }

        // REPLACE the list reference atomically instead of clearing/adding
        this.wheels = newWheels;
    }

    public List<Wheel> getWheels() {
        return wheels;
    }
}
