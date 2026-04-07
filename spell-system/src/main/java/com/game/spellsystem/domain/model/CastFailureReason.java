package com.game.spellsystem.domain.model;

public enum CastFailureReason {
    INSUFFICIENT_MANA,
    WAND_COOLDOWN,
    RECHARGING,
    EMPTY_DECK,
    NO_VALID_CARD,
    TRIGGER_DEPTH_EXCEEDED,
    PROJECTILE_BUDGET_EXCEEDED
}
