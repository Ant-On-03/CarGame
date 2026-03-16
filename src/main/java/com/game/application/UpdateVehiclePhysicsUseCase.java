package com.game.application;

import com.game.domain.vehicle.Car;
import com.game.domain.core.ControlInput;
import com.game.domain.physics.VehiclePhysicsEngine;

/**
 * Application-layer use case that advances the vehicle simulation
 * by one time step.
 * <p>
 * This thin wrapper provides an architectural boundary between the
 * application and domain layers. In a larger system it would also
 * handle validation, event emission, collision orchestration, etc.
 */
public class UpdateVehiclePhysicsUseCase {

    private final VehiclePhysicsEngine engine;

    public UpdateVehiclePhysicsUseCase(VehiclePhysicsEngine engine) {
        this.engine = engine;
    }

    /**
     * Executes one physics update.
     *
     * @param car   the car to update (mutated in place)
     * @param input player control inputs for this frame
     * @param dt    time step in seconds
     */
    public void execute(Car car, ControlInput input, double dt) {
        engine.update(car, input, dt);
    }
}
