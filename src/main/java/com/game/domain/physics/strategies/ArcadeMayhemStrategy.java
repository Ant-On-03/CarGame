package com.game.domain.physics.strategies;

import com.game.domain.vehicle.Car;
import com.game.domain.vehicle.CarConfig;
import com.game.domain.core.ControlInput;
import com.game.domain.vehicle.DriveType;
import com.game.domain.SurfaceType;
import com.game.domain.vehicle.Tire;
import com.game.domain.core.Vector2;
import com.game.domain.vehicle.Wheel;
import com.game.domain.physics.models.TireForceResult;
import com.game.domain.physics.models.WeightTransferCalculator;
import com.game.ports.TerrainProvider;

/**
 * "Arcade Mayhem" handling strategy.
 * <p>
 * Intentionally breaks the laws of physics to create a wild, expressive
 * driving feel. The car is a toy that players wrestle with — not a
 * frustrating pendulum, and not a boring easy-mode kart.
 * <p>
 * Three core hacks:
 * <ol>
 *   <li><b>Pod-Racer FWD</b> — FWD engine force is applied as a direct
 *       impulse to the centre of mass aimed where the front wheels point.
 *       Feels like being dragged by a grappling hook.</li>
 *   <li><b>Butter Drift</b> — beyond a slip-angle threshold, lateral grip
 *       drops to a low constant. Restoring torque is capped so the car
 *       slides smoothly instead of violent pendulum snap-backs.</li>
 *   <li><b>Downforce on Demand</b> — slamming throttle while turning
 *       multiplies tire grip. Coasting = slippery. Gas+steer = glued.</li>
 * </ol>
 * <p>
 * All tuning constants are clearly labelled and grouped at the top of the
 * class for easy tweaking.
 */
public class ArcadeMayhemStrategy implements HandlingStrategy {

    // ================================================================
    // TUNING CONSTANTS — tweak these to change the "weirdness"
    // ================================================================

    // ---- Speed-sensitive steering ----
    // Controls how much steering lock is reduced at speed.
    // Higher value = more reduction. At 0 the car has full lock at all speeds.
    // Formula: effectiveMaxAngle = maxSteeringAngle / (1 + speed * FACTOR)
    private static final double SPEED_STEERING_FACTOR = 0.075;  // ~18° at 144 km/h

    // ---- Butter Drift: slip-angle curve ----
    // BUTTER_SLIP_THRESHOLD: slip angle (radians) where the "butter zone" begins.
    //   Lower = car starts sliding sooner (more drifty).
    //   Higher = more grip before butter kicks in (more stable).
    //   15° is a good midpoint — moderate cornering triggers butter.
    private static final double BUTTER_SLIP_THRESHOLD = Math.toRadians(15);

    // BUTTER_GRIP_RATIO: fraction of peak lateral force kept in butter zone.
    //   0.10 = 10% = ice rink.  0.20 = 20% = controllable slide.
    //   0.15 is the sweet spot — enough force to slowly steer out, not enough to snap.
    private static final double BUTTER_GRIP_RATIO = 0.15;

    // BUTTER_TRANSITION_WIDTH: radians over which grip fades from full to butter.
    //   Wider = gentler entry into slide. Narrower = sharper "pop" into drift.
    //   8° gives a smooth, natural-feeling onset.
    private static final double BUTTER_TRANSITION_WIDTH = Math.toRadians(8);

    // MAX_RESTORING_FORCE: hard cap (Newtons) on lateral force while in butter zone.
    //   This is the kill switch for pendulum wobble. The snap-back torque that
    //   caused violent oscillation is capped at this value.
    //   Lower = smoother slides but less control.  Higher = more snap-back risk.
    //   2000 N is about 0.25g of lateral force on an 800 kg car — gentle nudge only.
    private static final double MAX_RESTORING_FORCE = 2000.0;

    // ---- Downforce on Demand: grip multiplier ----
    // DOWNFORCE_MULTIPLIER: how much extra grip when throttle + steer are both active.
    //   2.0 = double grip.  3.0 = triple grip (extremely sticky).
    //   2.5 is the sweet spot — physics-defying but not invincible.
    private static final double DOWNFORCE_MULTIPLIER = 2.5;

    // Minimum input thresholds to activate downforce boost (avoids noise)
    private static final double DOWNFORCE_MIN_THROTTLE = 0.1;
    private static final double DOWNFORCE_MIN_STEERING = 0.1;

    // ---- General physics ----
    private static final double STOP_SPEED_THRESHOLD = 0.1;
    private static final double MIN_BRAKE_SPEED = 0.3;
    private static final double MIN_SPEED_FOR_CORNERING = 0.5;

    // ---- Dependencies ----
    private final WeightTransferCalculator weightTransfer;

    public ArcadeMayhemStrategy(WeightTransferCalculator weightTransfer) {
        this.weightTransfer = weightTransfer;
    }

    @Override
    public String name() {
        return "Arcade Mayhem";
    }

    // ================================================================
    // MAIN PHYSICS STEP
    // ================================================================

    @Override
    public void applyForces(Car car, ControlInput input, TerrainProvider terrain, double dt) {
        CarConfig cfg = car.getConfig();
        Vector2 forward = car.forwardVector();

        // Weight transfer still distributes normal forces (affects grip budgets)
        double longAccel = estimateLongAccel(car, input, cfg);
        double latAccel = estimateLatAccel(car, forward);

        // Add 'dt' as the 4th argument here!
        weightTransfer.distribute(car, longAccel, latAccel, dt);

        // Speed-sensitive steering
        double speed = car.getSpeed();
        double effectiveMaxAngle = cfg.maxSteeringAngle() / (1.0 + speed * SPEED_STEERING_FACTOR);
        double steeringAngle = input.steering() * effectiveMaxAngle;

        // ---- Pod-Racer FWD mode ----
        // When FWD: engine force is NOT applied per-wheel (where friction circles
        // and weight transfer would eat it). Instead it's a direct CoM impulse
        // aimed in the steered direction. Feels like a grappling hook.
        boolean podRacerMode = (cfg.driveType() == DriveType.FWD);

        // ---- Per-wheel forces ----
        Vector2 totalForce = Vector2.ZERO;
        double totalTorque = 0.0;
        double totalExtraRolling = 0.0;

        for (Wheel wheel : car.getWheels()) {
            Vector2 wheelWorldPos = car.getPosition().add(
                    wheel.getLocalOffset().rotate(car.getRotation()));
            SurfaceType surface = terrain.getSurfaceAt(wheelWorldPos.x(), wheelWorldPos.y());

            WheelResult r = computeWheelForces(
                    car, wheel, input, steeringAngle, cfg,
                    surface.frictionMultiplier(), podRacerMode);
            totalForce = totalForce.add(r.force);
            totalTorque += r.torque;
            totalExtraRolling += surface.extraRollingResistance();
        }

        // ---- Pod-Racer FWD: direct CoM impulse ----
        if (podRacerMode && Math.abs(input.throttle()) > 0.0) {
            totalForce = totalForce.add(computePodRacerForce(car, input, steeringAngle, cfg));
        }

        // ---- Drag and rolling resistance ----
        totalForce = totalForce.add(computeDrag(car, cfg));
        totalForce = totalForce.add(computeRollingResistance(car, cfg, totalExtraRolling));

        // Angular damping (base value from CarConfig — butter drift handles stability)
        totalTorque -= cfg.angularDamping() * car.getAngularVelocity();

        // Arcade steering assist (direct yaw torque, speed-gated)
        double steerSpeedFactor = Math.min(speed / 5.0, 1.0);
        totalTorque += input.steering() * cfg.steeringAssistTorque() * steerSpeedFactor;

        // Integrate and stop
        integrate(car, cfg, totalForce, totalTorque, dt);
        applyStoppingLogic(car, input);
    }

    // ================================================================
    // POD-RACER FWD HACK
    // ================================================================

    /**
     * The "Pod-Racer" hack for FWD cars.
     * <p>
     * Full engine force applied as a direct vector to the centre of mass,
     * aimed exactly where the front wheels are pointed. Applied to CoM
     * means zero torque from propulsion — just pure directional pull.
     * <p>
     * Physically nonsensical. Incredibly punchy. The car goes where
     * the wheels point, instantly, with full engine force, unconstrained
     * by tire grip circles.
     */
    private Vector2 computePodRacerForce(Car car, ControlInput input,
                                         double steeringAngle, CarConfig cfg) {
        // Direction = car heading rotated by steering angle
        double podDirection = car.getRotation() + steeringAngle;
        Vector2 forceDir = Vector2.fromAngle(podDirection);
        return forceDir.multiply(input.throttle() * cfg.engineForce());
    }

    // ================================================================
    // PER-WHEEL FORCE COMPUTATION
    // ================================================================

    /**
     * Computes forces for a single wheel using the Butter Drift lateral
     * curve and Downforce on Demand friction multiplier.
     * <p>
     * Pod-Racer mode: engine force on driven (front) wheels is zeroed —
     * propulsion is handled as a CoM impulse instead.
     */
    private WheelResult computeWheelForces(Car car, Wheel wheel, ControlInput input,
                                           double steeringAngle, CarConfig cfg,
                                           double surfaceFriction, boolean podRacerMode) {
        Vector2 worldOffset = wheel.getLocalOffset().rotate(car.getRotation());
        Vector2 wheelVel = car.getVelocity().add(
                worldOffset.perpendicular().multiply(car.getAngularVelocity()));

        double wheelAngle = car.getRotation() + (wheel.isFront() ? steeringAngle : 0.0);
        Vector2 wheelFwd = Vector2.fromAngle(wheelAngle);
        Vector2 wheelRight = wheelFwd.perpendicular();
        double wheelLongVel = wheelVel.dot(wheelFwd);
        double wheelLatVel = wheelVel.dot(wheelRight);

        // ---- Longitudinal force (engine + brake) ----
        double longForce = 0.0;
        // Pod-Racer: skip engine on driven wheels (it's a CoM impulse instead)
        if (!podRacerMode && wheel.isDriven(cfg.driveType())) {
            longForce += input.throttle() * cfg.engineForce() / cfg.drivenWheelCount();
        }
        if (input.brake() > 0.0 && Math.abs(wheelLongVel) > MIN_BRAKE_SPEED) {
            longForce -= input.brake() * cfg.brakeForce() / 4.0 * Math.signum(wheelLongVel);
        }

        // ---- Lateral force (BUTTER DRIFT curve) ----
        double latForce = computeButterLateralForce(wheel.getTire(), wheelLongVel, wheelLatVel);

        // ---- Friction circle (DOWNFORCE ON DEMAND) ----
        TireForceResult result = applyArcadeFrictionLimit(
                wheel, longForce, latForce, surfaceFriction,
                input.throttle(), Math.abs(input.steering()));
        wheel.setSlipping(result.slipping());

        Vector2 worldForce = wheelFwd.multiply(result.longitudinalForce())
                .add(wheelRight.multiply(result.lateralForce()));
        return new WheelResult(worldForce, worldOffset.cross(worldForce));
    }

    // ================================================================
    // BUTTER DRIFT — custom slip-angle curve
    // ================================================================

    /**
     * The "Butter Drift" lateral force curve.
     * <p>
     * <b>Below threshold:</b> normal linear cornering stiffness. The car
     * grips and turns predictably.
     * <p>
     * <b>Above threshold:</b> lateral force exponentially fades down to
     * {@link #BUTTER_GRIP_RATIO} of the peak force. The car slides like
     * it's on butter — smooth, controllable, and (critically) without
     * snapping back.
     * <p>
     * <b>Hard cap:</b> restoring force is clamped to {@link #MAX_RESTORING_FORCE}
     * while in the butter zone. This is the kill switch for pendulum wobble.
     * The violent snap-back torque that caused oscillation is surgically
     * limited so the car can only gently nudge itself straight.
     *
     * @param tire     tire properties (cornering stiffness used for the linear zone)
     * @param longVel  wheel-frame forward speed (m/s)
     * @param latVel   wheel-frame sideways speed (m/s)
     * @return lateral force in Newtons
     */
    private double computeButterLateralForce(Tire tire, double longVel, double latVel) {
        double absLongSpeed = Math.abs(longVel);

        // Too slow for meaningful cornering — avoid division-by-near-zero
        if (absLongSpeed < MIN_SPEED_FOR_CORNERING
                && Math.abs(latVel) < MIN_SPEED_FOR_CORNERING) {
            return 0.0;
        }

        // Slip angle: how far the tire is pointing from where it's actually going
        double slipAngle = Math.atan2(latVel, absLongSpeed + MIN_SPEED_FOR_CORNERING);
        double absSlip = Math.abs(slipAngle);

        // Peak force = what cornering stiffness produces right at the threshold
        double peakForce = tire.corneringStiffness() * BUTTER_SLIP_THRESHOLD;

        double lateralForce;

        if (absSlip <= BUTTER_SLIP_THRESHOLD) {
            // ---- GRIP ZONE ----
            // Normal linear cornering stiffness. Predictable, responsive.
            lateralForce = -tire.corneringStiffness() * slipAngle;
        } else {
            // ---- BUTTER ZONE ----
            // Exponential decay from peak toward a low plateau.
            // The deeper into butter, the closer to BUTTER_GRIP_RATIO * peak.
            double excessSlip = absSlip - BUTTER_SLIP_THRESHOLD;
            double t = 1.0 - Math.exp(-excessSlip / BUTTER_TRANSITION_WIDTH);

            // Interpolate: peakForce → (peakForce * BUTTER_GRIP_RATIO)
            double butterForce = peakForce * BUTTER_GRIP_RATIO;
            double fadedForce = peakForce + (butterForce - peakForce) * t;

            // Apply sign (opposes lateral velocity)
            lateralForce = -Math.signum(slipAngle) * fadedForce;

            // ---- PENDULUM KILL SWITCH ----
            // Hard cap on restoring force. This is what stops the violent
            // snap-back oscillation. Even if cornering stiffness is huge,
            // the restoring torque in butter zone cannot exceed this cap.
            lateralForce = clamp(lateralForce, -MAX_RESTORING_FORCE, MAX_RESTORING_FORCE);
        }

        return lateralForce;
    }

    // ================================================================
    // DOWNFORCE ON DEMAND — friction circle with grip boost
    // ================================================================

    /**
     * Friction circle enforcement with "Downforce on Demand" grip boost.
     * <p>
     * When the player is simultaneously accelerating AND turning:
     * <ul>
     *   <li>Friction budget is multiplied by up to {@link #DOWNFORCE_MULTIPLIER}</li>
     *   <li>The boost ramps smoothly — full effect at 50%+ throttle and 50%+ steering</li>
     *   <li>Coasting into a turn = normal (low) grip → car is slippery</li>
     *   <li>Slamming gas mid-corner = boosted grip → car sticks impossibly</li>
     * </ul>
     * This creates a unique driving rhythm: coast to initiate a slide,
     * then gas to pull through it.
     */
    private TireForceResult applyArcadeFrictionLimit(Wheel wheel,
                                                     double desiredLong,
                                                     double desiredLat,
                                                     double surfaceFriction,
                                                     double throttle,
                                                     double steeringMag) {
        Tire tire = wheel.getTire();
        double normalForce = wheel.getNormalForce();

        if (normalForce <= 0.0) {
            return TireForceResult.NO_GRIP;
        }

        // ---- DOWNFORCE ON DEMAND ----
        // Compute grip boost factor from combined throttle + steering input.
        // Both must be above threshold. Ramp to full boost at 50% input.
        double downforceBoost = 1.0;
        if (throttle > DOWNFORCE_MIN_THROTTLE && steeringMag > DOWNFORCE_MIN_STEERING) {
            // Each input contributes independently, capped at 1.0
            double throttleFactor = Math.min(throttle / 0.5, 1.0);   // 0→1 over 0%→50% throttle
            double steerFactor = Math.min(steeringMag / 0.5, 1.0);   // 0→1 over 0%→50% steering
            // Multiply factors — both must be significant for full boost
            downforceBoost = 1.0 + (DOWNFORCE_MULTIPLIER - 1.0) * throttleFactor * steerFactor;
        }

        double effectiveFriction = surfaceFriction * downforceBoost;

        // Standard friction circle, but with the boosted friction
        double maxStatic = tire.maxGripForce(normalForce) * effectiveFriction;
        double desiredMag = Math.sqrt(desiredLong * desiredLong + desiredLat * desiredLat);

        if (desiredMag <= maxStatic) {
            return new TireForceResult(desiredLong, desiredLat, false);
        }

        double dynamicMax = tire.dynamicGripForce(normalForce) * effectiveFriction;
        double scale = dynamicMax / desiredMag;
        return new TireForceResult(desiredLong * scale, desiredLat * scale, true);
    }

    // ================================================================
    // SHARED UTILITIES
    // ================================================================

    private Vector2 computeDrag(Car car, CarConfig cfg) {
        double speed = car.getSpeed();
        return speed < 0.01 ? Vector2.ZERO : car.getVelocity().multiply(-cfg.dragCoefficient() * speed);
    }

    private Vector2 computeRollingResistance(Car car, CarConfig cfg, double extraRolling) {
        double speed = car.getSpeed();
        if (speed < 0.01) return Vector2.ZERO;
        double total = cfg.rollingResistance() + extraRolling / 4.0;
        return car.getVelocity().normalize().multiply(-total);
    }

    private double estimateLongAccel(Car car, ControlInput input, CarConfig cfg) {
        return input.throttle() * cfg.engineForce() / cfg.mass()
                - input.brake() * cfg.brakeForce() / cfg.mass() * Math.signum(car.getLongitudinalSpeed());
    }

    private double estimateLatAccel(Car car, Vector2 forward) {
        return car.getVelocity().dot(forward) * car.getAngularVelocity();
    }

    private void integrate(Car car, CarConfig cfg, Vector2 force, double torque, double dt) {
        Vector2 accel = force.multiply(1.0 / cfg.mass());
        car.setVelocity(car.getVelocity().add(accel.multiply(dt)));
        car.setPosition(car.getPosition().add(car.getVelocity().multiply(dt)));

        double angAccel = torque / cfg.momentOfInertia();
        car.setAngularVelocity(car.getAngularVelocity() + angAccel * dt);
        car.setRotation(car.getRotation() + car.getAngularVelocity() * dt);
    }

    private void applyStoppingLogic(Car car, ControlInput input) {
        if (car.getSpeed() < STOP_SPEED_THRESHOLD
                && input.throttle() == 0.0 && input.brake() == 0.0) {
            car.setVelocity(Vector2.ZERO);
        }
        if (Math.abs(car.getAngularVelocity()) < 0.01
                && car.getSpeed() < STOP_SPEED_THRESHOLD) {
            car.setAngularVelocity(0.0);
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private record WheelResult(Vector2 force, double torque) {}
}
