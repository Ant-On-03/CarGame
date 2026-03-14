package com.game.domain.physics;

import com.game.domain.Car;
import com.game.domain.CarConfig;
import com.game.domain.ControlInput;
import com.game.domain.Vector2;
import com.game.domain.Wheel;

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

    private final WeightTransferCalculator weightTransfer;
    private final TireForceModel tireForceModel;

    public VehiclePhysicsEngine(WeightTransferCalculator weightTransfer,
                                TireForceModel tireForceModel) {
        this.weightTransfer = weightTransfer;
        this.tireForceModel = tireForceModel;
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

        // 4. Steering angle
        double steeringAngle = input.steering() * cfg.maxSteeringAngle();

        // 5. Per-wheel force accumulation
        Vector2 totalForce = Vector2.ZERO;
        double totalTorque = 0.0;

        for (Wheel wheel : car.getWheels()) {
            WheelForceResult result = computeWheelForces(
                    car, wheel, input, steeringAngle, cfg);
            totalForce = totalForce.add(result.worldForce());
            totalTorque += result.torque();
        }

        // 6. Aerodynamic drag and rolling resistance
        totalForce = totalForce.add(computeDrag(car, cfg));
        totalForce = totalForce.add(computeRollingResistance(car, cfg));
        totalTorque -= cfg.angularDamping() * car.getAngularVelocity();

        // 6b. Arcade steering assist — direct yaw torque for instant response
        totalTorque += computeSteeringAssist(car, input, cfg);

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
                                                CarConfig cfg) {
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

        // Friction circle check
        TireForceResult tireResult = tireForceModel.applyFrictionLimit(
                wheel, desiredLongForce, desiredLatForce);
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

    private Vector2 computeRollingResistance(Car car, CarConfig cfg) {
        double speed = car.getSpeed();
        if (speed < 0.01) {
            return Vector2.ZERO;
        }
        return car.getVelocity().normalize().multiply(-cfg.rollingResistance());
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
