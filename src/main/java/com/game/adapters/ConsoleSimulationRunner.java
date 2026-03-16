package com.game.adapters;

import com.game.application.UpdateVehiclePhysicsUseCase;
import com.game.domain.vehicle.Car;
import com.game.domain.vehicle.CarConfig;
import com.game.domain.core.ControlInput;
import com.game.domain.SurfaceType;
import com.game.domain.core.Vector2;
import com.game.domain.vehicle.Wheel;
import com.game.domain.physics.strategies.ArcadeMayhemStrategy;
import com.game.domain.physics.VehiclePhysicsEngine;
import com.game.domain.physics.models.WeightTransferCalculator;

/**
 * Infrastructure adapter that runs a scripted physics simulation in the
 * console. Demonstrates the drifting physics without any graphical UI.
 * <p>
 * The simulation scripts a sequence of control inputs to:
 * <ol>
 *   <li>Accelerate straight (build speed)</li>
 *   <li>Steer hard while on throttle (initiate drift)</li>
 *   <li>Counter-steer with partial throttle (hold the drift)</li>
 *   <li>Straighten and coast (recover)</li>
 * </ol>
 * <p>
 * Uses a no-op terrain provider (always TARMAC) since there is no
 * procedural terrain in headless mode.
 */
public class ConsoleSimulationRunner {

    private static final double DT = 1.0 / 60.0;
    private static final int TOTAL_FRAMES = 360;  // 6 seconds
    private static final int PRINT_INTERVAL = 15; // print every 0.25s

    public static void main(String[] args) {
        CarConfig config = CarConfig.driftTuned();
        Car car = new Car(new Vector2(0, 0), config);

        UpdateVehiclePhysicsUseCase useCase = createUseCase();

        printHeader(config);

        double time = 0.0;
        for (int frame = 0; frame < TOTAL_FRAMES; frame++) {
            ControlInput input = scriptedInput(time);
            useCase.execute(car, input, DT);

            if (frame % PRINT_INTERVAL == 0) {
                printFrame(time, car, input);
            }

            time += DT;
        }

        printFooter();
    }

    // ---- Scripted input sequence ----

    private static ControlInput scriptedInput(double time) {
        if (time < 1.5) {
            // Phase 1: full throttle, straight ahead — build speed
            return new ControlInput(1.0, 0.0, 0.0);
        }
        if (time < 2.5) {
            // Phase 2: full throttle + hard right steer — initiate drift
            return new ControlInput(1.0, 0.0, 0.9);
        }
        if (time < 4.5) {
            // Phase 3: partial throttle + counter-steer left — hold drift
            return new ControlInput(0.7, 0.0, -0.4);
        }
        // Phase 4: ease off, straighten — recover
        return new ControlInput(0.2, 0.0, 0.0);
    }

    // ---- Output formatting ----

    private static void printHeader(CarConfig config) {
        System.out.println("=".repeat(90));
        System.out.println("  DRIFTING PHYSICS SIMULATION");
        System.out.println("=".repeat(90));
        System.out.printf("  Config: mass=%.0fkg  wheelbase=%.1fm  drive=%s%n",
                config.mass(), config.wheelbase(), config.driveType());
        System.out.printf("  Front tire: mu=%.2f  dynamic=%.2f  stiffness=%.0f N/rad%n",
                config.frontTire().frictionCoefficient(),
                config.frontTire().dynamicFrictionRatio(),
                config.frontTire().corneringStiffness());
        System.out.printf("  Rear  tire: mu=%.2f  dynamic=%.2f  stiffness=%.0f N/rad%n",
                config.rearTire().frictionCoefficient(),
                config.rearTire().dynamicFrictionRatio(),
                config.rearTire().corneringStiffness());
        System.out.println("-".repeat(90));
        System.out.printf("%-6s %-16s %8s %8s %6s %10s  %-20s  %-12s%n",
                "Time", "Position", "Speed", "LatSpd", "Angle", "AngVel",
                "Input(T/B/S)", "Slip");
        System.out.println("-".repeat(90));
    }

    private static void printFrame(double time, Car car, ControlInput input) {
        String pos = String.format("(%6.1f, %6.1f)",
                car.getPosition().x(), car.getPosition().y());
        String slip = formatSlip(car);

        System.out.printf("%5.2fs %-16s %7.2f %7.2f %+6.1f %+9.1f  T=%+.1f B=%.1f S=%+.1f  %s%n",
                time,
                pos,
                car.getSpeed(),
                car.getLateralSpeed(),
                Math.toDegrees(car.getRotation()),
                Math.toDegrees(car.getAngularVelocity()),
                input.throttle(),
                input.brake(),
                input.steering(),
                slip);
    }

    private static String formatSlip(Car car) {
        StringBuilder sb = new StringBuilder();
        for (Wheel w : car.getWheels()) {
            sb.append(w.isSlipping() ? "X" : ".");
        }
        return sb.toString();
    }

    private static void printFooter() {
        System.out.println("-".repeat(90));
        System.out.println("  Slip key: FL.FR.RL.RR   X=slipping  .=grip");
        System.out.println("  Phases: 0-1.5s accelerate | 1.5-2.5s drift entry | "
                + "2.5-4.5s counter-steer | 4.5-6s recover");
        System.out.println("=".repeat(90));
    }

    // ---- Wiring ----

    private static UpdateVehiclePhysicsUseCase createUseCase() {
        // No-op terrain provider: always returns TARMAC for headless simulation
        return new UpdateVehiclePhysicsUseCase(
                new VehiclePhysicsEngine(
                        new ArcadeMayhemStrategy(new WeightTransferCalculator()),
                        (x, y) -> SurfaceType.TARMAC
                )
        );
    }
}
