package com.game.adapters;

import com.game.ports.InputProvider;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Adapter that translates AWT {@link KeyListener} events into the
 * device-agnostic {@link InputProvider} port.
 * <p>
 * Controls (both layouts work simultaneously):
 * <ul>
 *     <li>W / UP    → throttle (1.0)</li>
 *     <li>S / DOWN  → brake (1.0)</li>
 *     <li>A / LEFT  → steer left (-1.0)</li>
 *     <li>D / RIGHT → steer right (+1.0)</li>
 *     <li>SPACE     → brake (1.0)</li>
 * </ul>
 */
public class KeyboardInputAdapter implements InputProvider, KeyListener {

    private final Set<Integer> pressedKeys = Collections.synchronizedSet(new HashSet<>());

    @Override
    public double getThrottle() {
        return isPressed(KeyEvent.VK_UP, KeyEvent.VK_W) ? 1.0 : 0.0;
    }

    @Override
    public double getBrake() {
        return isPressed(KeyEvent.VK_DOWN, KeyEvent.VK_S, KeyEvent.VK_SPACE) ? 1.0 : 0.0;
    }

    @Override
    public double getSteering() {
        boolean left = isPressed(KeyEvent.VK_LEFT, KeyEvent.VK_A);
        boolean right = isPressed(KeyEvent.VK_RIGHT, KeyEvent.VK_D);

        if (left && !right) {
            return -1.0;
        }
        if (right && !left) {
            return 1.0;
        }
        return 0.0;
    }

    private boolean isPressed(int... keyCodes) {
        for (int code : keyCodes) {
            if (pressedKeys.contains(code)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        pressedKeys.add(e.getKeyCode());
    }

    @Override
    public void keyReleased(KeyEvent e) {
        pressedKeys.remove(e.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Not used
    }
}
