package com.game.domain;

/**
 * Immutable 2D vector for physics calculations.
 * <p>
 * Coordinate system: X-right, Y-down (screen coordinates).
 * Angles: 0 = up (negative Y), clockwise positive.
 */
public record Vector2(double x, double y) {

    public static final Vector2 ZERO = new Vector2(0, 0);

    /**
     * Creates a unit direction vector from an angle.
     * Angle 0 points up (-Y), PI/2 points right (+X).
     */
    public static Vector2 fromAngle(double angle) {
        return new Vector2(Math.sin(angle), -Math.cos(angle));
    }

    public Vector2 add(Vector2 other) {
        return new Vector2(x + other.x, y + other.y);
    }

    public Vector2 subtract(Vector2 other) {
        return new Vector2(x - other.x, y - other.y);
    }

    public Vector2 multiply(double scalar) {
        return new Vector2(x * scalar, y * scalar);
    }

    public double dot(Vector2 other) {
        return x * other.x + y * other.y;
    }

    /**
     * 2D cross product (z-component of the 3D cross product).
     * Positive result indicates clockwise rotation in screen coordinates.
     */
    public double cross(Vector2 other) {
        return x * other.y - y * other.x;
    }

    public double magnitude() {
        return Math.sqrt(x * x + y * y);
    }

    public double magnitudeSquared() {
        return x * x + y * y;
    }

    public Vector2 normalize() {
        double mag = magnitude();
        return mag < 1e-10 ? ZERO : multiply(1.0 / mag);
    }

    /**
     * Rotates this vector by the given angle (clockwise in screen coords).
     */
    public Vector2 rotate(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vector2(
                x * cos - y * sin,
                x * sin + y * cos
        );
    }

    /**
     * Returns the perpendicular vector rotated 90 degrees counterclockwise.
     * For a forward vector, this yields the right vector in screen coords.
     */
    public Vector2 perpendicular() {
        return new Vector2(-y, x);
    }

    public Vector2 negate() {
        return new Vector2(-x, -y);
    }

    @Override
    public String toString() {
        return String.format("(%.3f, %.3f)", x, y);
    }
}
