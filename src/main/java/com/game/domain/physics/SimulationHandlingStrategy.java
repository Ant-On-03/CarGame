package com.game.domain.physics;

import com.game.domain.Car;
import com.game.domain.CarConfig;
import com.game.domain.ControlInput;
import com.game.domain.SurfaceType;
import com.game.domain.Vector2;
import com.game.domain.Wheel;
import com.game.ports.TerrainProvider;

/**
 * Baseline "simulation" handling strategy.
 * <p>
 * Uses realistic tire-friction circles, weight transfer, linearised
 * slip-angle cornering, and standard aerodynamic drag. This is the
 * physically-accurate reference model — it will feel twitchy on
 * keyboard because real cars don't have binary steering, but it is
 * correct for the underlying math.
 * <p>
 * Includes quality-of-life assists that don't break the physics:
 * <ul>
 *   <li>Speed-sensitive steering reduction (narrower lock at speed)</li>
 *   <li>Speed-proportional angular damping (resist yaw at speed)</li>
 *   <li>Counter-steer drift assist (gentle torque toward velocity)</li>
 *   <li>Arcade steering assist torque (from CarConfig)</li>
 * </ul>
 */
public class SimulationHandlingStrategy implements HandlingStrategy {

    private static final double STOP_SPEED_THRESHOLD = 0.1;
    private static final double MIN_BRAKE_SPEED = 0.3;

    // Speed-sensitive steering: full lock at standstill, ~18° at 144 km/h
    private static final double SPEED_STEERING_FACTOR = 0.075;

    // Speed-proportional angular damping (N-m-s²/rad/m)
    private static final double SPEED_ANGULAR_DAMPING_FACTOR = 30.0;

    // Drift assist: corrective torque toward velocity when rear tires slip
    private static final double DRIFT_ASSIST_GAIN = 3000.0;
    private static final double DRIFT_ASSIST_MIN_SPEED = 3.0;

    private final WeightTransferCalculator weightTransfer;
    private final TireForceModel tireForceModel;

    public SimulationHandlingStrategy(WeightTransferCalculator weightTransfer,
                                      TireForceModel tireForceModel) {
        this.weightTransfer = weightTransfer;
        this.tireForceModel = tireForceModel;
    }

    @Override
    public String name() {
        return "Simulation";
    }

    @Override
    public void applyForces(Car car, ControlInput input, TerrainProvider terrain, double dt) {
        CarConfig cfg = car.getConfig();
        Vector2 forward = car.forwardVector();

        // Weight transfer → normal forces
        double longAccel = estimateLongAccel(car, input, cfg);
        double latAccel = estimateLatAccel(car, forward);
        weightTransfer.distribute(car, longAccel, latAccel);

        // Speed-sensitive steering
        double speed = car.getSpeed();
        double effectiveMaxAngle = cfg.maxSteeringAngle() / (1.0 + speed * SPEED_STEERING_FACTOR);
        double steeringAngle = input.steering() * effectiveMaxAngle;

        // Per-wheel forces
        Vector2 totalForce = Vector2.ZERO;
        double totalTorque = 0.0;
        double totalExtraRolling = 0.0;

        for (Wheel wheel : car.getWheels()) {
            Vector2 wheelWorldPos = car.getPosition().add(
                    wheel.getLocalOffset().rotate(car.getRotation()));
            SurfaceType surface = terrain.getSurfaceAt(wheelWorldPos.x(), wheelWorldPos.y());

            WheelResult r = computeWheelForces(car, wheel, input, steeringAngle, cfg,
                    surface.frictionMultiplier());
            totalForce = totalForce.add(r.force);
            totalTorque += r.torque;
            totalExtraRolling += surface.extraRollingResistance();
        }

        // Drag + rolling resistance
        totalForce = totalForce.add(computeDrag(car, cfg));
        totalForce = totalForce.add(computeRollingResistance(car, cfg, totalExtraRolling));

        // Speed-dependent angular damping
        double effectiveDamping = cfg.angularDamping() + speed * SPEED_ANGULAR_DAMPING_FACTOR;
        totalTorque -= effectiveDamping * car.getAngularVelocity();

        // Steering assist
        double speedFactor = Math.min(speed / 5.0, 1.0);
        totalTorque += input.steering() * cfg.steeringAssistTorque() * speedFactor;

        // Drift assist (counter-steer)
        totalTorque += computeDriftAssist(car, forward, speed);

        // Integrate
        integrate(car, cfg, totalForce, totalTorque, dt);
        applyStoppingLogic(car, input);
    }

    // ---- Per-wheel ----

    private WheelResult computeWheelForces(Car car, Wheel wheel, ControlInput input,
                                           double steeringAngle, CarConfig cfg,
                                           double surfaceFriction) {
        Vector2 worldOffset = wheel.getLocalOffset().rotate(car.getRotation());
        Vector2 wheelVel = car.getVelocity().add(
                worldOffset.perpendicular().multiply(car.getAngularVelocity()));

        double wheelAngle = car.getRotation() + (wheel.isFront() ? steeringAngle : 0.0);
        Vector2 wheelFwd = Vector2.fromAngle(wheelAngle);
        Vector2 wheelRight = wheelFwd.perpendicular();
        double wheelLongVel = wheelVel.dot(wheelFwd);
        double wheelLatVel = wheelVel.dot(wheelRight);

        double longForce = 0.0;
        if (wheel.isDriven(cfg.driveType())) {
            longForce += input.throttle() * cfg.engineForce() / cfg.drivenWheelCount();
        }
        if (input.brake() > 0.0 && Math.abs(wheelLongVel) > MIN_BRAKE_SPEED) {
            longForce -= input.brake() * cfg.brakeForce() / 4.0 * Math.signum(wheelLongVel);
        }

        double latForce = tireForceModel.computeLateralForce(
                wheel.getTire(), wheelLongVel, wheelLatVel);

        TireForceResult result = tireForceModel.applyFrictionLimit(
                wheel, longForce, latForce, surfaceFriction);
        wheel.setSlipping(result.slipping());

        Vector2 worldForce = wheelFwd.multiply(result.longitudinalForce())
                .add(wheelRight.multiply(result.lateralForce()));
        return new WheelResult(worldForce, worldOffset.cross(worldForce));
    }

    // ---- Drift assist ----

    private double computeDriftAssist(Car car, Vector2 forward, double speed) {
        if (speed < DRIFT_ASSIST_MIN_SPEED) return 0.0;

        boolean rearSlip = false;
        for (Wheel w : car.getWheels()) {
            if (w.isRear() && w.isSlipping()) { rearSlip = true; break; }
        }
        if (!rearSlip) return 0.0;

        double misalignment = forward.cross(car.getVelocity().normalize());
        return misalignment * DRIFT_ASSIST_GAIN * Math.min(speed / 10.0, 1.0);
    }

    // ---- Drag ----

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

    // ---- Acceleration estimates ----

    private double estimateLongAccel(Car car, ControlInput input, CarConfig cfg) {
        return input.throttle() * cfg.engineForce() / cfg.mass()
                - input.brake() * cfg.brakeForce() / cfg.mass() * Math.signum(car.getLongitudinalSpeed());
    }

    private double estimateLatAccel(Car car, Vector2 forward) {
        return car.getVelocity().dot(forward) * car.getAngularVelocity();
    }

    // ---- Integration ----

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

    private record WheelResult(Vector2 force, double torque) {}
}
