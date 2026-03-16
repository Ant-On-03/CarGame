package com.game.main;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/**
 * LibGDX desktop entry point.
 * <p>
 * Replaces the old AWT/Swing Main.java. This class solely configures
 * the OpenGL window and launches the game lifecycle manager (CarGameCore).
 */
public class DesktopLauncher {

    private static final String TITLE = "Car Physics — Drift Edition";
    private static final int WINDOW_WIDTH = 1024;
    private static final int WINDOW_HEIGHT = 768;

    public static void main(String[] arg) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();

        // Window settings
        config.setTitle(TITLE);
        config.setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT);
        config.setResizable(false);

        // Performance settings
        config.useVsync(true);
        config.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate);
        config.setIdleFPS(30); // Reduce CPU/GPU usage when window is minimized/unfocused

        // Launch the game!
        new Lwjgl3Application(new CarGameCore(), config);
    }
}