package com.game.domain.physics;

/**
 * Result of applying the friction circle limit to a single tire.
 *
 * @param longitudinalForce actual longitudinal force the tire can produce (N)
 * @param lateralForce      actual lateral force the tire can produce (N)
 * @param slipping          {@code true} if the tire has exceeded its grip limit
 */
public record TireForceResult(double longitudinalForce, double lateralForce, boolean slipping) {

    /** Zero-force result for wheels with no ground contact. */
    public static final TireForceResult NO_GRIP = new TireForceResult(0, 0, true);
}
