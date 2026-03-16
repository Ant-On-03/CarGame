package com.game.domain.physics.models;

import com.game.domain.vehicle.Tire;
import com.game.domain.vehicle.Wheel;

/**
 * Computes tire forces and enforces the friction circle limit.
 * <p>
 * The friction circle is the fundamental concept: each tire can only
 * produce a total force (combining longitudinal and lateral) up to
 * a maximum defined by {@code normalForce * frictionCoefficient}.
 * <p>
 * When the requested force exceeds this limit, the tire transitions
 * from static to dynamic friction — it begins to <em>slip</em>.
 * During slip, forces are scaled down by the {@code dynamicFrictionRatio},
 * which is always less than 1.0. This reduction in lateral grip is
 * what produces drifting.
 *
 * @see TireForceResult
 */
public class TireForceModel {

    private static final double MIN_SPEED_FOR_CORNERING = 0.5;

    /**
     * Computes the lateral (cornering) force a tire wants to produce
     * to resist sideways motion.
     * <p>
     * Uses a linearised slip-angle model:
     * <pre>
     *   slipAngle   = atan2(lateralVelocity, |longitudinalVelocity|)
     *   lateralForce = -corneringStiffness * slipAngle
     * </pre>
     * The negative sign means the force opposes the lateral motion.
     *
     * @param tire                  tire properties
     * @param longitudinalVelocity  wheel-frame forward speed (m/s)
     * @param lateralVelocity       wheel-frame sideways speed (m/s, positive = right)
     * @return desired lateral force (N); negative = pushes left
     */
    public double computeLateralForce(Tire tire, double longitudinalVelocity,
                                      double lateralVelocity) {
        double absLongSpeed = Math.abs(longitudinalVelocity);

        if (absLongSpeed < MIN_SPEED_FOR_CORNERING
                && Math.abs(lateralVelocity) < MIN_SPEED_FOR_CORNERING) {
            return 0.0;
        }

        double slipAngle = Math.atan2(lateralVelocity, absLongSpeed + MIN_SPEED_FOR_CORNERING);
        return -tire.corneringStiffness() * slipAngle;
    }

    /**
     * Enforces the friction circle: if the combined desired force
     * exceeds the tire's grip, the wheel starts slipping and forces
     * are reduced to the dynamic friction level.
     * <p>
     * The {@code surfaceFriction} multiplier scales the grip limit
     * to simulate different terrain surfaces (e.g. 1.0 for tarmac,
     * 0.12 for ice).
     *
     * @param wheel             the wheel (normal force must already be set)
     * @param desiredLongForce  requested longitudinal force (N)
     * @param desiredLatForce   requested lateral force (N)
     * @param surfaceFriction   terrain friction multiplier (0.0 - 1.0)
     * @return the actual force the tire can produce, plus slip state
     */
    public TireForceResult applyFrictionLimit(Wheel wheel,
                                              double desiredLongForce,
                                              double desiredLatForce,
                                              double surfaceFriction) {
        Tire tire = wheel.getTire();
        double normalForce = wheel.getNormalForce();

        if (normalForce <= 0.0) {
            return TireForceResult.NO_GRIP;
        }

        double maxStaticForce = tire.maxGripForce(normalForce) * surfaceFriction;
        double desiredMagnitude = Math.sqrt(
                desiredLongForce * desiredLongForce + desiredLatForce * desiredLatForce);

        if (desiredMagnitude <= maxStaticForce) {
            return new TireForceResult(desiredLongForce, desiredLatForce, false);
        }

        double dynamicMax = tire.dynamicGripForce(normalForce) * surfaceFriction;
        double scale = dynamicMax / desiredMagnitude;

        return new TireForceResult(
                desiredLongForce * scale,
                desiredLatForce * scale,
                true
        );
    }
}
