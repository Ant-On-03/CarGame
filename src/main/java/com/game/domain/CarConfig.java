package com.game.domain;

/**
 * Immutable vehicle configuration. All physical parameters that define
 * how the car behaves are collected here for easy tuning.
 * <p>
 * Units are SI: metres, kilograms, Newtons, radians.
 *
 * @param mass                 total vehicle mass (kg)
 * @param wheelbase            distance between front and rear axles (m)
 * @param trackWidth           distance between left and right wheels (m)
 * @param cgHeight             centre-of-gravity height above ground (m)
 * @param bodyLength           bumper-to-bumper length (m)
 * @param bodyWidth            total body width (m)
 * @param maxSteeringAngle     maximum front-wheel steering angle (radians)
 * @param engineForce          peak engine drive force (N)
 * @param brakeForce           peak brake force per axle (N); split across 4 wheels
 * @param driveType            which wheels receive engine torque
 * @param dragCoefficient      quadratic aerodynamic drag constant
 * @param rollingResistance    constant rolling-resistance force (N)
 * @param angularDamping       yaw-rate damping coefficient (N-m-s/rad)
 * @param steeringAssistTorque arcade-style direct yaw torque applied per unit of steering
 *                             input, scaled by speed (N-m); bypasses tire physics for
 *                             instant turn-in feel
 * @param frontTire            tire properties for the front axle
 * @param rearTire             tire properties for the rear axle
 */
public record CarConfig(
        double mass,
        double wheelbase,
        double trackWidth,
        double cgHeight,
        double bodyLength,
        double bodyWidth,
        double maxSteeringAngle,
        double engineForce,
        double brakeForce,
        DriveType driveType,
        double dragCoefficient,
        double rollingResistance,
        double angularDamping,
        double steeringAssistTorque,
        Tire frontTire,
        Tire rearTire
) {

    /**
     * Returns a configuration tuned for aggressive RWD drifting.
     * <p>
     * Key tuning choices:
     * <ul>
     *   <li>RWD so only rear wheels receive engine force</li>
     *   <li>Rear tire friction slightly lower than front → easier to break loose</li>
     *   <li>Lower dynamic friction ratio on rear → more slide once grip is lost</li>
     *   <li>High engine force relative to rear tire grip → wheelspin under full throttle</li>
     * </ul>
     */
    public static CarConfig driftTuned() {
        return new CarConfig(
                800.0,                      // mass (kg) — lighter = more reactive
                2.0,                        // wheelbase (m) — shorter = quicker rotation
                1.4,                        // trackWidth (m)
                0.40,                       // cgHeight (m)
                3.5,                        // bodyLength (m)
                1.6,                        // bodyWidth (m)
                Math.toRadians(75),         // maxSteeringAngle — nearly full lock
                80000.0,                    // engineForce (N) — absurd power, overpowers everything
                20000.0,                    // brakeForce (N)
                DriveType.RWD,
                0.8,                        // dragCoefficient — minimal drag, insane top speed
                60.0,                       // rollingResistance (N)
                50.0,                       // angularDamping (N-m-s/rad) — almost zero, razor sharp yaw
                15000.0,                    // steeringAssistTorque (N-m) — massive assist
                Tire.gripFront(),
                Tire.driftRear()
        );
    }

    /**
     * Moment of inertia about the vertical (yaw) axis,
     * approximated as a uniform rectangular plate.
     */
    public double momentOfInertia() {
        return mass * (bodyLength * bodyLength + bodyWidth * bodyWidth) / 12.0;
    }

    /**
     * Number of wheels that receive engine torque.
     */
    public int drivenWheelCount() {
        return driveType == DriveType.AWD ? 4 : 2;
    }
}
