package com.game.domain;

/**
 * Determines which wheels receive engine torque.
 */
public enum DriveType {

    /** Front-wheel drive: engine force applied to front wheels only. */
    FWD,

    /** Rear-wheel drive: engine force applied to rear wheels only. */
    RWD,

    /** All-wheel drive: engine force split equally across all four wheels. */
    AWD
}
