package com.game.domain.physics.models;

import com.game.domain.vehicle.Car;
import com.game.domain.vehicle.CarConfig;
import com.game.domain.vehicle.Wheel;

/**
 * Computes the normal (vertical) force on each wheel based on
 * longitudinal and lateral accelerations.
 * <p>
 * Weight transfer shifts load between wheels during acceleration,
 * braking, and cornering. This directly affects the grip available
 * at each tire via the friction circle.
 * <p>
 * Formulas:
 * <pre>
 *   longTransfer = (mass * longAccel * cgHeight) / wheelbase
 *   latTransfer  = (mass * latAccel  * cgHeight) / trackWidth
 * </pre>
 * <p>
 * Sign conventions:
 * <ul>
 *   <li>longAccel &gt; 0 (accelerating forward) → weight shifts to rear</li>
 *   <li>latAccel &gt; 0 (accelerating rightward) → weight shifts to left (outside)</li>
 * </ul>
 */


public class WeightTransferCalculator {

    private static final double GRAVITY = 9.81;

    // --- Suspension State ---
    private double smoothedLongAccel = 0.0;
    private double smoothedLatAccel = 0.0;

    // How fast the chassis responds to G-forces.
    // 12.0 = Stiff sports car (fast weight transfer)
    // 4.0 = Soft bouncy truck (slow weight transfer)
    private static final double SUSPENSION_RESPONSE = 8.0;

    /**
     * Distributes normal forces to all four wheels of the car.
     *
     * @param car             the vehicle whose wheels will be updated
     * @param targetLongAccel estimated longitudinal acceleration (m/s^2)
     * @param targetLatAccel  estimated lateral acceleration (m/s^2)
     * @param dt              time step in seconds for frame-rate independent smoothing
     */
    public void distribute(Car car, double targetLongAccel, double targetLatAccel, double dt) {

        // Frame-rate independent exponential smoothing to simulate suspension lag
        double factor = 1.0 - Math.exp(-SUSPENSION_RESPONSE * dt);
        smoothedLongAccel += (targetLongAccel - smoothedLongAccel) * factor;
        smoothedLatAccel += (targetLatAccel - smoothedLatAccel) * factor;

        CarConfig cfg = car.getConfig();

        double totalWeight = cfg.mass() * GRAVITY;
        double baseLoad = totalWeight / 4.0;

        // Calculate transfer using the SMOOTHED accelerations
        double longTransfer = computeLongitudinalTransfer(cfg, smoothedLongAccel);
        double latTransfer = computeLateralTransfer(cfg, smoothedLatAccel);

        for (Wheel wheel : car.getWheels()) {
            double load = baseLoad;
            load += wheel.isFront() ? -longTransfer / 2.0 : longTransfer / 2.0;
            load += wheel.getPosition().isLeft() ? latTransfer / 2.0 : -latTransfer / 2.0;
            wheel.setNormalForce(load);
        }
    }

    private double computeLongitudinalTransfer(CarConfig cfg, double longAccel) {
        return (cfg.mass() * longAccel * cfg.cgHeight()) / cfg.wheelbase();
    }

    private double computeLateralTransfer(CarConfig cfg, double latAccel) {
        return (cfg.mass() * latAccel * cfg.cgHeight()) / cfg.trackWidth();
    }
}