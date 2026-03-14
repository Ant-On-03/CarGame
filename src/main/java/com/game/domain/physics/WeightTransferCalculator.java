package com.game.domain.physics;

import com.game.domain.Car;
import com.game.domain.CarConfig;
import com.game.domain.Wheel;

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

    /**
     * Distributes normal forces to all four wheels of the car.
     *
     * @param car       the vehicle whose wheels will be updated
     * @param longAccel estimated longitudinal acceleration (m/s^2, positive = forward)
     * @param latAccel  estimated lateral acceleration (m/s^2, positive = rightward)
     */
    public void distribute(Car car, double longAccel, double latAccel) {
        CarConfig cfg = car.getConfig();

        double totalWeight = cfg.mass() * GRAVITY;
        double baseLoad = totalWeight / 4.0;

        double longTransfer = computeLongitudinalTransfer(cfg, longAccel);
        double latTransfer = computeLateralTransfer(cfg, latAccel);

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
