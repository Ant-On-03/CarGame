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

    private static final int DEFAULT_PHYSICS_HZ = 120;
    private static final long TARGET_FRAME_NS = 16_666_667L; // ~60 FPS cap
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /** Conversion factor from physics metres to screen pixels. */
    static final double PIXELS_PER_METER = 8.0;

    /**
     * Wheel visual position as a fraction of half the body width/length.
     * Calibrated from default CarConfig: trackWidth=1.4 / bodyWidth=1.6 → 0.4375,
     * wheelbase=2.0 / bodyLength=3.5 → 0.2857.
     * This ensures wheels scale visually when body dimensions change in the tuning panel,
     * while physics positions (trackWidth/wheelbase) remain independent.
     */
    private static final double WHEEL_X_BODY_FRACTION = 0.4375;
    private static final double WHEEL_Y_BODY_FRACTION = 0.2857;

    private final Car car;
    private final UpdateVehiclePhysicsUseCase physics;
    private final InputProvider input;
    private final Renderer renderer;
    private final double physicsDt;

    private volatile boolean running;
    private ControlInput lastControlInput = ControlInput.NONE;

    /**
     * Creates a game loop with the default physics tick rate (120 Hz).
     * Override via system property {@code -Dphysics.hz=60} for weaker hardware.
     */
    public GameLoop(Car car, UpdateVehiclePhysicsUseCase physics,
                    InputProvider input, Renderer renderer) {
        this(car, physics, input, renderer, resolvePhysicsHz());
    }

    /**
     * Creates a game loop with an explicit physics tick rate.
     *
     * @param physicsHz physics updates per second (e.g. 60 or 120)
     */
    public GameLoop(Car car, UpdateVehiclePhysicsUseCase physics,
                    InputProvider input, Renderer renderer, int physicsHz) {
        this.car = car;
        this.physics = physics;
        this.input = input;
        this.renderer = renderer;
        this.physicsDt = 1.0 / physicsHz;
    }

    /**
     * Reads desired physics tick rate from the {@code physics.hz} system property.
     * Falls back to {@value #DEFAULT_PHYSICS_HZ} if not set or invalid.
     */
    private static int resolvePhysicsHz() {
        String prop = System.getProperty("physics.hz");
        if (prop != null) {
            try {
                int hz = Integer.parseInt(prop.trim());
                if (hz >= 30 && hz <= 240) {
                    return hz;
                }
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return DEFAULT_PHYSICS_HZ;
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
            while (accumulator >= physicsDt) {
                physics.execute(car, controlInput, physicsDt);
                accumulator -= physicsDt;
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
        double carX = car.getPosition().x() * ppm;
        double carY = car.getPosition().y() * ppm;
        double rot = car.getRotation();

        WheelRenderData[] wheelData = new WheelRenderData[wheels.size()];
        for (int i = 0; i < wheels.size(); i++) {
            Wheel w = wheels.get(i);
            double localPxX = w.getLocalOffset().x() * ppm;
            double localPxY = w.getLocalOffset().y() * ppm;

            // Rotate local offset by car rotation to get world pixel position
            Vector2 rotated = new Vector2(localPxX, localPxY).rotate(rot);
            double worldPxX = carX + rotated.x();
            double worldPxY = carY + rotated.y();

            double wheelSteer = w.isFront() ? steeringAngle : 0.0;
            wheelData[i] = new WheelRenderData(
                    localPxX, localPxY,
                    worldPxX, worldPxY,
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
