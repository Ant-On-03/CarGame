package com.game.ports;

/**
 * Port for rendering the game world.
 * <p>
 * The domain calls these methods to describe what should be drawn,
 * without knowing anything about the underlying graphics API.
 */
public interface Renderer {

    /**
     * Prepares a new frame. Called once at the start of each render cycle.
     */
    void beginFrame();

    /**
     * Draws the car at the given world-space position and rotation.
     *
     * @param x        centre X in pixels
     * @param y        centre Y in pixels
     * @param rotation rotation in radians (0 = up, clockwise positive)
     * @param width    car width in pixels
     * @param length   car length in pixels
     * @param velocity current velocity (can be used for visual effects)
     * @param wheels   per-wheel rendering data (positions in pixels, forces, slip state)
     */
    void drawCar(double x, double y, double rotation, double width, double length,
                 double velocity, WheelRenderData[] wheels);

    /**
     * Finalises the frame and presents it to the screen.
     */
    void endFrame();

    /**
     * Returns the width of the renderable area in pixels.
     */
    int getCanvasWidth();

    /**
     * Returns the height of the renderable area in pixels.
     */
    int getCanvasHeight();
}
