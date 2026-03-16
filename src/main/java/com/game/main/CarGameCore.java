package com.game.main;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.game.adapters.LibGDXInputAdapter;
import com.game.adapters.LibGDXRendererAdapter;
import com.game.adapters.terrain.TerrainChunkGenerator;
// import com.game.adapters.ParameterTuningOverlay; // (Assuming you adapt this to LibGDX next)
import com.game.application.UpdateVehiclePhysicsUseCase;
import com.game.domain.core.ControlInput;
import com.game.domain.core.Vector2;
import com.game.domain.physics.VehiclePhysicsEngine;
import com.game.domain.physics.models.TireForceModel;
import com.game.domain.physics.models.WeightTransferCalculator;
import com.game.domain.physics.strategies.ArcadeMayhemStrategy;
import com.game.domain.physics.strategies.SimulationHandlingStrategy;
import com.game.domain.vehicle.Car;
import com.game.domain.vehicle.CarConfig;
import com.game.domain.vehicle.Wheel;
import com.game.domain.world.Camera;
import com.game.domain.world.terrain.ElevationFunction;
import com.game.domain.world.terrain.NoiseGenerator;
import com.game.domain.world.terrain.SinusoidalElevation;
import com.game.domain.world.terrain.SurfaceClassifier;
import com.game.domain.world.terrain.TerrainConfig;
import com.game.domain.world.terrain.ValueNoiseGenerator;
import com.game.ports.WheelRenderData;

import java.util.List;

/**
 * Core LibGDX Application Lifecycle Manager.
 * <p>
 * Replaces the old Main.java wiring and GameLoop.java fixed-timestep loop.
 * Integrates the pure Java domain (physics, terrain generation) with the
 * LibGDX hardware-accelerated adapters.
 */
public class CarGameCore extends ApplicationAdapter {

    private static final double PHYSICS_HZ = 120.0;
    private static final double PHYSICS_DT = 1.0 / PHYSICS_HZ;
    private static final double PIXELS_PER_METER = 8.0;

    // Steering smoothing rates
    private static final double STEERING_ATTACK_RATE = 4.0;
    private static final double STEERING_RELEASE_RATE = 8.0;

    // Domain & Use Cases
    private Car car;
    private Camera camera;
    private UpdateVehiclePhysicsUseCase physics;

    // Adapters
    private LibGDXInputAdapter inputAdapter;
    private LibGDXRendererAdapter rendererAdapter;

    // State
    private double accumulator = 0.0;
    private double smoothedSteering = 0.0;
    private ControlInput lastControlInput = ControlInput.NONE;

    @Override
    public void create() {
        // --- Terrain (Domain) ---
        NoiseGenerator noise = new ValueNoiseGenerator(42L);
        TerrainConfig terrainConfig = TerrainConfig.defaults();
        SurfaceClassifier surfaceClassifier = new SurfaceClassifier(noise, terrainConfig);
        ElevationFunction elevation = new SinusoidalElevation();

        // --- Terrain (Adapters) ---
        TerrainChunkGenerator chunkGenerator = new TerrainChunkGenerator(
                surfaceClassifier, elevation, terrainConfig);

        // --- Hardware Adapters (LibGDX) ---
        inputAdapter = new LibGDXInputAdapter();
        rendererAdapter = new LibGDXRendererAdapter();

        // --- Domain Entities ---
        CarConfig config = CarConfig.driftTuned();
        double startX = 0.0;
        double startY = 0.0;
        car = new Car(new Vector2(startX, startY), config);
        camera = new Camera(startX, startY);

        // --- Physics Engine (Composed from domain services) ---
        WeightTransferCalculator weightTransfer = new WeightTransferCalculator();
        ArcadeMayhemStrategy arcadeStrategy = new ArcadeMayhemStrategy(weightTransfer);
        SimulationHandlingStrategy simStrategy = new SimulationHandlingStrategy(
                weightTransfer, new TireForceModel());

        // Physics uses the TerrainChunkGenerator as its TerrainProvider port
        VehiclePhysicsEngine physicsEngine = new VehiclePhysicsEngine(arcadeStrategy, chunkGenerator);
        physics = new UpdateVehiclePhysicsUseCase(physicsEngine);

        // (Note: To re-add the tuning overlay, you would instantiate a LibGDX version
        // of ParameterTuningOverlay here and pass it to the input/renderer adapters)
    }

    @Override
    public void render() {
        // 1. Calculate delta time and cap it to avoid spiral-of-death during lag spikes
        double dt = Gdx.graphics.getDeltaTime();
        if (dt > 0.25) {
            dt = 0.25;
        }
        accumulator += dt;

        // 2. Poll hardware input
        double rawThrottle = inputAdapter.getThrottle();
        double rawBrake = inputAdapter.getBrake();
        double rawSteering = inputAdapter.getSteering();

        // 3. Fixed-Timestep Physics Loop (Guarantees deterministic physics)
        while (accumulator >= PHYSICS_DT) {
            smoothedSteering = smoothSteering(smoothedSteering, rawSteering, PHYSICS_DT);
            ControlInput smoothedInput = new ControlInput(rawThrottle, rawBrake, smoothedSteering);

            physics.execute(car, smoothedInput, PHYSICS_DT);
            accumulator -= PHYSICS_DT;
        }

        lastControlInput = new ControlInput(rawThrottle, rawBrake, smoothedSteering);

        // 4. Update Camera
        Vector2 vel = car.getVelocity();
        camera.update(car.getPosition().x(), car.getPosition().y(), vel.x(), vel.y(), dt);

        // 5. Hardware Rendering
        renderGraphics();
    }

    private void renderGraphics() {
        // Tell the renderer where the camera is
        rendererAdapter.setCamera(camera.getX(), camera.getY(), PIXELS_PER_METER);

        // Extract wheel data for rendering
        List<Wheel> wheels = car.getWheels();
        double steeringAngle = lastControlInput.steering() * car.getConfig().maxSteeringAngle();
        double carX = car.getPosition().x() * PIXELS_PER_METER;
        double carY = car.getPosition().y() * PIXELS_PER_METER;
        double rot = car.getRotation();

        WheelRenderData[] wheelData = new WheelRenderData[wheels.size()];
        for (int i = 0; i < wheels.size(); i++) {
            Wheel w = wheels.get(i);
            double localPxX = w.getLocalOffset().x() * PIXELS_PER_METER;
            double localPxY = w.getLocalOffset().y() * PIXELS_PER_METER;

            // Rotate local offset by car rotation to get world pixel position (for tire marks)
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

        // Draw the frame
        rendererAdapter.beginFrame();
        rendererAdapter.drawCar(
                car.getPosition().x() * PIXELS_PER_METER,
                car.getPosition().y() * PIXELS_PER_METER,
                car.getRotation(),
                car.getConfig().bodyWidth() * PIXELS_PER_METER,
                car.getConfig().bodyLength() * PIXELS_PER_METER,
                car.getSpeed(),
                wheelData
        );
        rendererAdapter.endFrame();
    }

    /**
     * Smooths steering input with different attack and release rates.
     */
    private static double smoothSteering(double current, double target, double dt) {
        double diff = target - current;
        boolean releasing = (target == 0.0) || (Math.signum(target) != Math.signum(current) && Math.abs(current) > 0.01);
        double rate = releasing ? STEERING_RELEASE_RATE : STEERING_ATTACK_RATE;
        double maxDelta = rate * dt;

        if (Math.abs(diff) <= maxDelta) {
            return target;
        }
        return current + Math.signum(diff) * maxDelta;
    }

    @Override
    public void dispose() {
        // LibGDX requires explicit disposal of C++ side memory (like Textures, SpriteBatches, ShapeRenderers).
        // Since they live inside your adapters, you might want to add a dispose() method
        // to your Renderer port eventually.
    }
}