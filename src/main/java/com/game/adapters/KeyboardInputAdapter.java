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
 * <p>
 * When the tuning overlay is visible, arrow keys are reserved for
 * panel navigation. Only WASD + Space remain active for driving.
 */
public class KeyboardInputAdapter implements InputProvider, KeyListener {

    private final Set<Integer> pressedKeys = Collections.synchronizedSet(new HashSet<>());

    /** When non-null and visible, arrow keys are consumed by the overlay instead of driving. */
    private ParameterTuningOverlay tuningOverlay;

    public void setTuningOverlay(ParameterTuningOverlay overlay) {
        this.tuningOverlay = overlay;
    }

    private boolean arrowsAvailableForDriving() {
        return tuningOverlay == null || !tuningOverlay.isVisible();
    }

    @Override
    public double getThrottle() {
        boolean w = isPressed(KeyEvent.VK_W);
        boolean up = arrowsAvailableForDriving() && isPressed(KeyEvent.VK_UP);
        return (w || up) ? 1.0 : 0.0;
    }

    @Override
    public double getBrake() {
        boolean s = isPressed(KeyEvent.VK_S);
        boolean down = arrowsAvailableForDriving() && isPressed(KeyEvent.VK_DOWN);
        boolean space = isPressed(KeyEvent.VK_SPACE);
        return (s || down || space) ? 1.0 : 0.0;
    }

    @Override
    public double getSteering() {
        boolean leftW = isPressed(KeyEvent.VK_A);
        boolean rightW = isPressed(KeyEvent.VK_D);
        boolean leftArr = arrowsAvailableForDriving() && isPressed(KeyEvent.VK_LEFT);
        boolean rightArr = arrowsAvailableForDriving() && isPressed(KeyEvent.VK_RIGHT);

        boolean left = leftW || leftArr;
        boolean right = rightW || rightArr;

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
