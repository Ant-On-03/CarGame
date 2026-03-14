package com.game.main;

import com.game.adapters.Java2DRendererAdapter;
import com.game.adapters.KeyboardInputAdapter;
import com.game.adapters.ParameterTuningOverlay;
import com.game.application.UpdateVehiclePhysicsUseCase;
import com.game.domain.Car;
import com.game.domain.CarConfig;
import com.game.domain.Vector2;
import com.game.domain.physics.TireForceModel;
import com.game.domain.physics.VehiclePhysicsEngine;
import com.game.domain.physics.WeightTransferCalculator;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Application entry point.
 * <p>
 * Wires the hexagonal architecture together:
 * domain objects, physics engine, port implementations (adapters),
 * and the game loop.
 */
public class Main {

    private static final String TITLE = "Car Physics — Drift Edition";
    private static final int WINDOW_WIDTH = 1024;
    private static final int WINDOW_HEIGHT = 768;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::launch);
    }

    private static void launch() {
        // --- Adapters ---
        KeyboardInputAdapter inputAdapter = new KeyboardInputAdapter();
        Java2DRendererAdapter rendererAdapter =
                new Java2DRendererAdapter(WINDOW_WIDTH, WINDOW_HEIGHT);

        // --- Window ---
        JFrame frame = createWindow(rendererAdapter, inputAdapter);

        // --- Domain ---
        CarConfig config = CarConfig.driftTuned();
        double startX = (WINDOW_WIDTH / GameLoop.PIXELS_PER_METER) / 2.0;
        double startY = (WINDOW_HEIGHT / GameLoop.PIXELS_PER_METER) / 2.0;
        Car car = new Car(new Vector2(startX, startY), config);

        // --- Parameter tuning overlay (Tab to toggle) ---
        ParameterTuningOverlay tuningOverlay = new ParameterTuningOverlay(car);
        rendererAdapter.setTuningOverlay(tuningOverlay);
        rendererAdapter.addKeyListener(tuningOverlay);
        inputAdapter.setTuningOverlay(tuningOverlay);

        // --- Physics engine (composed from domain services) ---
        UpdateVehiclePhysicsUseCase physics = new UpdateVehiclePhysicsUseCase(
                new VehiclePhysicsEngine(
                        new WeightTransferCalculator(),
                        new TireForceModel()
                )
        );

        // --- Game loop ---
        GameLoop gameLoop = new GameLoop(car, physics, inputAdapter, rendererAdapter);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                gameLoop.stop();
                frame.dispose();
                System.exit(0);
            }
        });

        rendererAdapter.initBufferStrategy();

        Thread gameThread = new Thread(gameLoop, "GameLoop");
        gameThread.setDaemon(true);
        gameThread.start();
    }

    private static JFrame createWindow(Java2DRendererAdapter canvas,
                                       KeyboardInputAdapter input) {
        JFrame frame = new JFrame(TITLE);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setResizable(false);

        canvas.setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
        canvas.addKeyListener(input);
        canvas.setFocusable(true);
        // Disable default focus traversal so Tab reaches our KeyListeners
        canvas.setFocusTraversalKeysEnabled(false);

        frame.add(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        canvas.requestFocusInWindow();

        return frame;
    }
}
