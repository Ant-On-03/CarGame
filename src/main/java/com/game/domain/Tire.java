package com.game.domain;

/**
 * Immutable tire properties that define grip behaviour.
 *
 * @param frictionCoefficient  static friction coefficient (typically 0.7 - 1.2)
 * @param dynamicFrictionRatio ratio of dynamic-to-static friction (less than 1.0);
 *                             lower values mean more grip loss when slipping
 * @param corneringStiffness   lateral force per radian of slip angle (N/rad);
 *                             higher values produce snappier cornering response
 */
public record Tire(
        double frictionCoefficient,
        double dynamicFrictionRatio,
        double corneringStiffness
) {

    public static Tire gripFront() {
        return new Tire(1.5, 0.75, 300_000);
    }

    public static Tire driftRear() {
        return new Tire(2.0, 0.65, 40_000);
    }

    /**
     * Maximum force this tire can produce before slipping.
     */
    public double maxGripForce(double normalForce) {
        return normalForce * frictionCoefficient;
    }

    /**
     * Maximum force this tire can produce while slipping (dynamic friction).
     */
    public double dynamicGripForce(double normalForce) {
        return normalForce * frictionCoefficient * dynamicFrictionRatio;
    }
}
