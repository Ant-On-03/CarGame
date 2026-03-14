package com.game.domain.physics;

import com.game.domain.Car;
import com.game.domain.CarConfig;
import com.game.domain.ControlInput;
import com.game.domain.SurfaceType;
import com.game.domain.Vector2;
import com.game.domain.Wheel;
import com.game.ports.TerrainProvider;

/**
 * Top-level physics service that advances a car's state by one time step.
 * <p>
 * Algorithm per frame:
 * <ol>
 *   <li>Compute the car's forward and right basis vectors from rotation.</li>
 *   <li>Estimate longitudinal and lateral accelerations for weight transfer.</li>
 *   <li>Distribute normal forces to all four wheels.</li>
 *   <li>Compute per-wheel forces (engine, brake, cornering) and check
 *       each wheel against its friction circle.</li>
 *   <li>Sum forces and torques from all wheels; add drag and damping.</li>
 *   <li>Integrate velocity and position (semi-implicit Euler).</li>
 *   <li>Clamp near-zero velocities to prevent numerical drift.</li>
 * </ol>
 * <p>
 * This class is stateless; all mutable state lives in the {@link Car}.
 */
public class VehiclePhysicsEngine {

    private static final double STOP_SPEED_THRESHOLD = 0.1;
    private static final double MIN_BRAKE_SPEED = 0.3;

    /**
     * Speed-sensitive steering factor.
     * Formula: effectiveMaxAngle = maxSteeringAngle / (1 + speed * SPEED_STEERING_FACTOR)
     * At 0 m/s: full lock (75°). At 40 m/s (~144 km/h): 75°/4 ≈ 18.75°.
     */
    private static final double SPEED_STEERING_FACTOR = 0.075;

    /**
     * Speed-proportional angular damping coefficient (N-m-s²/rad/m).
     * Added to the base angularDamping to increase yaw resistance at speed.
     * Total damping = baseDamping + speed * SPEED_ANGULAR_DAMPING_FACTOR.
     */
    private static final double SPEED_ANGULAR_DAMPING_FACTOR = 30.0;

    /**
     * Drift-assist (counter-steer) torque gain.
     * When rear tires slip, a corrective torque nudges the car's heading
     * toward its velocity vector, making drifts recoverable.
     */
    private static final double DRIFT_ASSIST_GAIN = 3000.0;

    /**
     * Minimum speed (m/s) to engage drift assist. Below this the assist
     * is not needed and would fight low-speed manoeuvring.
     */
    private static final double DRIFT_ASSIST_MIN_SPEED = 3.0;

    private final WeightTransferCalculator weightTransfer;
    private final TireForceModel tireForceModel;
    private final TerrainProvider terrainProvider;

    public VehiclePhysicsEngine(WeightTransferCalculator weightTransfer,
                                TireForceModel tireForceModel,
                                TerrainProvider terrainProvider) {
        this.weightTransfer = weightTransfer;
        this.tireForceModel = tireForceModel;
        this.terrainProvider = terrainProvider;
    }

    /**
     * Advances the car's physics state by {@code dt} seconds.
     *
     * @param car   the car to update (mutated in place)
     * @param input player control inputs for this frame
     * @param dt    time step in seconds
     */
    public void update(Car car, ControlInput input, double dt) {
        CarConfig cfg = car.getConfig();

        // 1. Basis vectors
        Vector2 forward = car.forwardVector();
        Vector2 right = car.rightVector();

        // 2. Estimate accelerations for weight transfer
        double longAccel = estimateLongitudinalAcceleration(car, input, cfg);
        double latAccel = estimateLateralAcceleration(car, forward);

        // 3. Weight transfer → normal forces
        weightTransfer.distribute(car, longAccel, latAccel);

        // 4. Speed-sensitive steering angle
        //    Full lock at standstill, reduced at speed to prevent snap oversteer.
        double speed = car.getSpeed();
        double effectiveMaxAngle = cfg.maxSteeringAngle() / (1.0 + speed * SPEED_STEERING_FACTOR);
        double steeringAngle = input.steering() * effectiveMaxAngle;

        // 5. Per-wheel force accumulation
        Vector2 totalForce = Vector2.ZERO;
        double totalTorque = 0.0;
        double totalExtraRollingResistance = 0.0;

        for (Wheel wheel : car.getWheels()) {
            // Query terrain surface at this wheel's world position
            Vector2 wheelWorldPos = car.getPosition().add(
                    wheel.getLocalOffset().rotate(car.getRotation()));
            SurfaceType surface = terrainProvider.getSurfaceAt(
                    wheelWorldPos.x(), wheelWorldPos.y());

            WheelForceResult result = computeWheelForces(
                    car, wheel, input, steeringAngle, cfg, surface.frictionMultiplier());
            totalForce = totalForce.add(result.worldForce());
            totalTorque += result.torque();

            // Accumulate extra rolling resistance from surface (split per wheel)
            totalExtraRollingResistance += surface.extraRollingResistance();
        }

        // 6. Aerodynamic drag and rolling resistance
        totalForce = totalForce.add(computeDrag(car, cfg));
        totalForce = totalForce.add(computeRollingResistance(car, cfg, totalExtraRollingResistance));

        // Speed-dependent angular damping: base + proportional-to-speed term
        double effectiveDamping = cfg.angularDamping() + speed * SPEED_ANGULAR_DAMPING_FACTOR;
        totalTorque -= effectiveDamping * car.getAngularVelocity();

        // 6b. Arcade steering assist — direct yaw torque for instant response
        totalTorque += computeSteeringAssist(car, input, cfg);

        // 6c. Drift assist — counter-steer torque when rear tires are slipping
        totalTorque += computeDriftAssist(car, forward);

        // 7. Integration (semi-implicit Euler)
        integrate(car, cfg, totalForce, totalTorque, dt);

        // 8. Stop the car cleanly at very low speeds
        applyStoppingLogic(car, input);
    }

    // ---- Acceleration estimates (for weight transfer) ----

    private double estimateLongitudinalAcceleration(Car car, ControlInput input,
                                                    CarConfig cfg) {
        double engineAccel = input.throttle() * cfg.engineForce() / cfg.mass();
        double brakeAccel = -input.brake() * cfg.brakeForce() / cfg.mass()
                * Math.signum(car.getLongitudinalSpeed());
        return engineAccel + brakeAccel;
    }

    private double estimateLateralAcceleration(Car car, Vector2 forward) {
        double forwardSpeed = car.getVelocity().dot(forward);
        return forwardSpeed * car.getAngularVelocity();
    }

    // ---- Per-wheel force computation ----

    private WheelForceResult computeWheelForces(Car car, Wheel wheel,
                                                ControlInput input,
                                                double steeringAngle,
                                                CarConfig cfg,
                                                double surfaceFriction) {
        // World-space offset from car centre to wheel
        Vector2 worldOffset = wheel.getLocalOffset().rotate(car.getRotation());

        // Wheel velocity = car velocity + rotational contribution
        Vector2 rotationalVelocity = worldOffset.perpendicular()
                .multiply(car.getAngularVelocity());
        Vector2 wheelVelocity = car.getVelocity().add(rotationalVelocity);

        // Wheel heading (front wheels turn with steering)
        double wheelAngle = car.getRotation();
        if (wheel.isFront()) {
            wheelAngle += steeringAngle;
        }

        // Decompose wheel velocity into wheel-local frame
        Vector2 wheelForward = Vector2.fromAngle(wheelAngle);
        Vector2 wheelRight = wheelForward.perpendicular();
        double wheelLongVel = wheelVelocity.dot(wheelForward);
        double wheelLatVel = wheelVelocity.dot(wheelRight);

        // Desired longitudinal force
        double desiredLongForce = computeDesiredLongForce(
                wheel, input, cfg, wheelLongVel);

        // Desired lateral force (cornering resistance)
        double desiredLatForce = tireForceModel.computeLateralForce(
                wheel.getTire(), wheelLongVel, wheelLatVel);

        // Friction circle check (with terrain surface multiplier)
        TireForceResult tireResult = tireForceModel.applyFrictionLimit(
                wheel, desiredLongForce, desiredLatForce, surfaceFriction);
        wheel.setSlipping(tireResult.slipping());

        // Convert wheel-local force to world coordinates
        Vector2 worldForce = wheelForward.multiply(tireResult.longitudinalForce())
                .add(wheelRight.multiply(tireResult.lateralForce()));

        // Torque about car centre
        double torque = worldOffset.cross(worldForce);

        return new WheelForceResult(worldForce, torque);
    }

    private double computeDesiredLongForce(Wheel wheel, ControlInput input,
                                           CarConfig cfg, double wheelLongVel) {
        double force = 0.0;

        // Engine force (only on driven wheels)
        if (wheel.isDriven(cfg.driveType())) {
            force += input.throttle() * cfg.engineForce() / cfg.drivenWheelCount();
        }

        // Brake force (all four wheels, opposes longitudinal velocity)
        if (input.brake() > 0.0 && Math.abs(wheelLongVel) > MIN_BRAKE_SPEED) {
            force -= input.brake() * cfg.brakeForce() / 4.0 * Math.signum(wheelLongVel);
        }

        return force;
    }

    // ---- Drag and resistance ----

    private Vector2 computeDrag(Car car, CarConfig cfg) {
        double speed = car.getSpeed();
        if (speed < 0.01) {
            return Vector2.ZERO;
        }
        return car.getVelocity().multiply(-cfg.dragCoefficient() * speed);
    }

    /**
     * Computes rolling resistance including extra resistance from terrain surface.
     * The extra resistance is averaged across 4 wheels and applied as a force
     * opposing the direction of motion.
     */
    private Vector2 computeRollingResistance(Car car, CarConfig cfg,
                                              double totalExtraRollingResistance) {
        double speed = car.getSpeed();
        if (speed < 0.01) {
            return Vector2.ZERO;
        }
        // Average extra resistance across 4 wheels
        double totalResistance = cfg.rollingResistance() + totalExtraRollingResistance / 4.0;
        return car.getVelocity().normalize().multiply(-totalResistance);
    }

    // ---- Steering assist ----

    /**
     * Arcade-style direct yaw torque that makes steering feel instant.
     * Scales with speed so the car doesn't spin on the spot when stationary.
     * The speed factor saturates at ~10 m/s for full effect.
     */
    private double computeSteeringAssist(Car car, ControlInput input, CarConfig cfg) {
        double speed = car.getSpeed();
        double speedFactor = Math.min(speed / 5.0, 1.0);
        return input.steering() * cfg.steeringAssistTorque() * speedFactor;
    }

    // ---- Drift assist ----

    /**
     * Counter-steer assist ("drift assist").
     * <p>
     * When the car is sliding (heading differs from velocity direction),
     * this applies a corrective yaw torque that gently nudges the heading
     * toward the velocity vector. The result is that drifts feel heroic
     * and recoverable instead of instantly spiralling into a 360.
     * <p>
     * The torque is proportional to the cross product of the forward vector
     * and the velocity direction (measures misalignment), scaled by speed.
     * Only active when at least one rear tire is slipping.
     */
    private double computeDriftAssist(Car car, Vector2 forward) {
        double speed = car.getSpeed();
        if (speed < DRIFT_ASSIST_MIN_SPEED) {
            return 0.0;
        }

        // Check if any rear tire is slipping
        boolean rearSlipping = false;
        for (Wheel wheel : car.getWheels()) {
            if (wheel.isRear() && wheel.isSlipping()) {
                rearSlipping = true;
                break;
            }
        }
        if (!rearSlipping) {
            return 0.0;
        }

        // Misalignment: cross(forward, velDir) gives signed angular error
        Vector2 velDir = car.getVelocity().normalize();
        double misalignment = forward.cross(velDir);

        // Scale by speed — stronger correction at higher speed where spins are more dangerous
        double speedFactor = Math.min(speed / 10.0, 1.0);
        return misalignment * DRIFT_ASSIST_GAIN * speedFactor;
    }

    // ---- Integration ----

    private void integrate(Car car, CarConfig cfg,
                           Vector2 totalForce, double totalTorque, double dt) {
        // Linear: semi-implicit Euler (update velocity first, then position)
        Vector2 linearAccel = totalForce.multiply(1.0 / cfg.mass());
        car.setVelocity(car.getVelocity().add(linearAccel.multiply(dt)));
        car.setPosition(car.getPosition().add(car.getVelocity().multiply(dt)));

        // Angular
        double angularAccel = totalTorque / cfg.momentOfInertia();
        car.setAngularVelocity(car.getAngularVelocity() + angularAccel * dt);
        car.setRotation(car.getRotation() + car.getAngularVelocity() * dt);
    }

    private void applyStoppingLogic(Car car, ControlInput input) {
        if (car.getSpeed() < STOP_SPEED_THRESHOLD
                && input.throttle() == 0.0
                && input.brake() == 0.0) {
            car.setVelocity(Vector2.ZERO);
        }

        if (Math.abs(car.getAngularVelocity()) < 0.01
                && car.getSpeed() < STOP_SPEED_THRESHOLD) {
            car.setAngularVelocity(0.0);
        }
    }

    // ---- Internal result record ----

    private record WheelForceResult(Vector2 worldForce, double torque) {}
}
