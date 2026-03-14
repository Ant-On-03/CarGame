package com.game.ports;

/**
 * Rendering-only snapshot of a single wheel's state.
 * Passed across the port boundary so the renderer never touches domain types.
 *
 * @param offsetX      wheel X offset from car centre, in pixels (car-local frame)
 * @param offsetY      wheel Y offset from car centre, in pixels (car-local frame)
 * @param steeringAngle  steering rotation for this wheel (radians); 0 for rear wheels
 * @param normalForce  downward force on this wheel (N)
 * @param slipping     true if the wheel has exceeded its friction circle
 */
public record WheelRenderData(
        double offsetX,
        double offsetY,
        double steeringAngle,
        double normalForce,
        boolean slipping
) {}
