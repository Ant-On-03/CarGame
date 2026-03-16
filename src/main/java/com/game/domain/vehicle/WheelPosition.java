package com.game.domain.vehicle;

/**
 * Identifies each of the four wheels on the vehicle.
 */
public enum WheelPosition {

    FRONT_LEFT(true, true),
    FRONT_RIGHT(true, false),
    REAR_LEFT(false, true),
    REAR_RIGHT(false, false);

    private final boolean front;
    private final boolean leftSide;

    WheelPosition(boolean front, boolean leftSide) {
        this.front = front;
        this.leftSide = leftSide;
    }

    public boolean isFront() {
        return front;
    }

    public boolean isRear() {
        return !front;
    }

    public boolean isLeft() {
        return leftSide;
    }

    public boolean isRight() {
        return !leftSide;
    }
}
