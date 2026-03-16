package com.game.adapters;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.game.ports.InputProvider;

/**
 * Adapter that translates LibGDX hardware input into the
 * device-agnostic {@link InputProvider} port.
 * <p>
 * Controls (both layouts work simultaneously):
 * <ul>
 * <li>W / UP    → throttle (1.0)</li>
 * <li>S / DOWN  → brake (1.0)</li>
 * <li>A / LEFT  → steer left (-1.0)</li>
 * <li>D / RIGHT → steer right (+1.0)</li>
 * <li>SPACE     → brake (1.0)</li>
 * </ul>
 * <p>
 * When the tuning overlay is visible, arrow keys are reserved for
 * panel navigation. Only WASD + Space remain active for driving.
 */
public class LibGDXInputAdapter implements InputProvider {

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
        boolean w = Gdx.input.isKeyPressed(Input.Keys.W);
        boolean up = arrowsAvailableForDriving() && Gdx.input.isKeyPressed(Input.Keys.UP);

        return (w || up) ? 1.0 : 0.0;
    }

    @Override
    public double getBrake() {
        boolean s = Gdx.input.isKeyPressed(Input.Keys.S);
        boolean down = arrowsAvailableForDriving() && Gdx.input.isKeyPressed(Input.Keys.DOWN);
        boolean space = Gdx.input.isKeyPressed(Input.Keys.SPACE);

        return (s || down || space) ? 1.0 : 0.0;
    }

    @Override
    public double getSteering() {
        boolean leftW = Gdx.input.isKeyPressed(Input.Keys.A);
        boolean rightW = Gdx.input.isKeyPressed(Input.Keys.D);
        boolean leftArr = arrowsAvailableForDriving() && Gdx.input.isKeyPressed(Input.Keys.LEFT);
        boolean rightArr = arrowsAvailableForDriving() && Gdx.input.isKeyPressed(Input.Keys.RIGHT);

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
}