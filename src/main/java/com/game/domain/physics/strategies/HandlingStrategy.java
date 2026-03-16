package com.game.domain.physics.strategies;

import com.game.domain.vehicle.Car;
import com.game.domain.core.ControlInput;
import com.game.domain.physics.VehiclePhysicsEngine;
import com.game.ports.TerrainProvider;

/**
 * Strategy interface for vehicle handling models.
 * <p>
 * Each implementation defines a complete physics "feel" — how forces
 * are computed, how tires grip, how the car responds to input.
 * Implementations are hot-swappable at runtime via
 * {@link VehiclePhysicsEngine#setHandlingStrategy(HandlingStrategy)}.
 * <p>
 * The strategy is responsible for the full physics step: computing
 * forces, integrating velocity and position, and applying any
 * stopping logic. This gives each strategy full control over the
 * driving feel without being constrained by a shared integration step.
 * <p>
 * Domain-only: no AWT/Swing imports allowed.
 */
public interface HandlingStrategy {

    /**
     * Computes and applies all forces for one physics time step.
     * <p>
     * The implementation must:
     * <ol>
     *   <li>Compute per-wheel and body forces from the car state and input</li>
     *   <li>Integrate velocity, position, and angular velocity</li>
     *   <li>Apply any stopping/clamping logic</li>
     * </ol>
     *
     * @param car     the car to update (mutated in place)
     * @param input   player control inputs for this tick
     * @param terrain provides surface type at any world coordinate
     * @param dt      fixed time step in seconds
     */
    void applyForces(Car car, ControlInput input, TerrainProvider terrain, double dt);

    /**
     * Human-readable name for this handling model (shown in UI/debug).
     */
    String name();
}
