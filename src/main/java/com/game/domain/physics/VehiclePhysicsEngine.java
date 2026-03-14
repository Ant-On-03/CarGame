package com.game.domain.physics;

import com.game.domain.Car;
import com.game.domain.ControlInput;
import com.game.ports.TerrainProvider;

/**
 * Strategy Pattern context for vehicle physics.
 * <p>
 * Holds a {@link HandlingStrategy} and delegates the physics step to it.
 * The strategy can be swapped at runtime via {@link #setHandlingStrategy}
 * to change the driving feel without restarting the game.
 * <p>
 * This class is the single entry point for all physics updates.
 * The {@link com.game.application.UpdateVehiclePhysicsUseCase} calls
 * {@link #update} each tick — it doesn't know or care which strategy
 * is active underneath.
 */
public class VehiclePhysicsEngine {

    private final TerrainProvider terrainProvider;
    private HandlingStrategy strategy;

    /**
     * Creates the engine with an initial handling strategy.
     *
     * @param strategy        the initial physics model to use
     * @param terrainProvider terrain surface lookup for per-wheel friction
     */
    public VehiclePhysicsEngine(HandlingStrategy strategy,
                                TerrainProvider terrainProvider) {
        this.strategy = strategy;
        this.terrainProvider = terrainProvider;
    }

    /**
     * Advances the car's physics state by {@code dt} seconds.
     * Delegates entirely to the active {@link HandlingStrategy}.
     *
     * @param car   the car to update (mutated in place)
     * @param input player control inputs for this tick
     * @param dt    time step in seconds
     */
    public void update(Car car, ControlInput input, double dt) {
        strategy.applyForces(car, input, terrainProvider, dt);
    }

    /**
     * Hot-swaps the handling strategy at runtime.
     * Takes effect on the next call to {@link #update}.
     *
     * @param strategy the new physics model
     */
    public void setHandlingStrategy(HandlingStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Returns the currently active handling strategy.
     */
    public HandlingStrategy getHandlingStrategy() {
        return strategy;
    }
}
