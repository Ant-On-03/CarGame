package com.game.main;

import com.game.application.UpdateVehiclePhysicsUseCase;
import com.game.domain.Car;
import com.game.domain.ControlInput;
import com.game.domain.Vector2;
import com.game.domain.Wheel;
import com.game.ports.InputProvider;
import com.game.ports.Renderer;
import com.game.ports.WheelRenderData;

/**
 * Fixed-timestep game loop that coordinates input, physics, and rendering.
 * <p>
 * Physics runs at a fixed rate (default 120 Hz) to guarantee deterministic
 * behaviour regardless of frame rate. Rendering happens as fast as possible
 * after each batch of physics updates.
 * <p>
 * The physics domain operates in SI units (metres). A constant
 * {@link #PIXELS_PER_METER} converts to screen coordinates for rendering.
 */
public class GameLoop implements Runnable {

    private static final double PHYSICS_DT = 1.0 / 120.0;
    private static final long TARGET_FRAME_NS = 16_666_667L; // ~60 FPS cap
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /** Conversion factor from physics metres to screen pixels. */
    static final double PIXELS_PER_METER = 8.0;

    private final Car car;
    private final UpdateVehiclePhysicsUseCase physics;
    private final InputProvider input;
    private final Renderer renderer;

    private volatile boolean running;
    private ControlInput lastControlInput = ControlInput.NONE;

    public GameLoop(Car car, UpdateVehiclePhysicsUseCase physics,
                    InputProvider input, Renderer renderer) {
        this.car = car;
        this.physics = physics;
        this.input = input;
        this.renderer = renderer;
    }

    @Override
    public void run() {
        running = true;
        long previousTime = System.nanoTime();
        double accumulator = 0.0;

        while (running) {
            long currentTime = System.nanoTime();
            double elapsed = (currentTime - previousTime) / (double) NANOS_PER_SECOND;
            previousTime = currentTime;

            // Guard against spiral-of-death: cap the elapsed time
            if (elapsed > 0.25) {
                elapsed = 0.25;
            }

            accumulator += elapsed;

            // Read input once per frame
            ControlInput controlInput = new ControlInput(
                    input.getThrottle(),
                    input.getBrake(),
                    input.getSteering()
            );
            lastControlInput = controlInput;

            // Fixed-timestep physics updates
            while (accumulator >= PHYSICS_DT) {
                physics.execute(car, controlInput, PHYSICS_DT);
                accumulator -= PHYSICS_DT;
            }

            wrapAroundScreen();
            render();
            sleepUntilNextFrame(currentTime);
        }
    }

    public void stop() {
        running = false;
    }

    // ---- Rendering (physics metres → screen pixels) ----

    private void render() {
        double ppm = PIXELS_PER_METER;

        // Build per-wheel rendering snapshots
        java.util.List<Wheel> wheels = car.getWheels();
        double steeringAngle = lastControlInput.steering() * car.getConfig().maxSteeringAngle();
        WheelRenderData[] wheelData = new WheelRenderData[wheels.size()];
        for (int i = 0; i < wheels.size(); i++) {
            Wheel w = wheels.get(i);
            double wheelSteer = w.isFront() ? steeringAngle : 0.0;
            wheelData[i] = new WheelRenderData(
                    w.getLocalOffset().x() * ppm,
                    w.getLocalOffset().y() * ppm,
                    wheelSteer,
                    w.getNormalForce(),
                    w.isSlipping()
            );
        }

        renderer.beginFrame();
        renderer.drawCar(
                car.getPosition().x() * ppm,
                car.getPosition().y() * ppm,
                car.getRotation(),
                car.getConfig().bodyWidth() * ppm,
                car.getConfig().bodyLength() * ppm,
                car.getSpeed(),
                wheelData
        );
        renderer.endFrame();
    }

    // ---- Screen wrapping (in physics space) ----

    private void wrapAroundScreen() {
        int canvasW = renderer.getCanvasWidth();
        int canvasH = renderer.getCanvasHeight();

        if (canvasW == 0 || canvasH == 0) {
            return;
        }

        double worldWidth = canvasW / PIXELS_PER_METER;
        double worldHeight = canvasH / PIXELS_PER_METER;
        double margin = Math.max(car.getConfig().bodyWidth(),
                car.getConfig().bodyLength());

        double x = car.getPosition().x();
        double y = car.getPosition().y();
        boolean wrapped = false;

        if (x < -margin) {
            x = worldWidth + margin;
            wrapped = true;
        } else if (x > worldWidth + margin) {
            x = -margin;
            wrapped = true;
        }

        if (y < -margin) {
            y = worldHeight + margin;
            wrapped = true;
        } else if (y > worldHeight + margin) {
            y = -margin;
            wrapped = true;
        }

        if (wrapped) {
            car.setPosition(new Vector2(x, y));
        }
    }

    // ---- Frame pacing ----

    private void sleepUntilNextFrame(long frameStartNanos) {
        long frameDurationNanos = System.nanoTime() - frameStartNanos;
        long sleepNanos = TARGET_FRAME_NS - frameDurationNanos;

        if (sleepNanos > 0) {
            try {
                long sleepMs = sleepNanos / 1_000_000;
                int sleepRemainderNanos = (int) (sleepNanos % 1_000_000);
                Thread.sleep(sleepMs, sleepRemainderNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stop();
            }
        }
    }
}
