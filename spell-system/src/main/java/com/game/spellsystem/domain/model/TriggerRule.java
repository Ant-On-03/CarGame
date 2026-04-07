package com.game.spellsystem.domain.model;

import java.util.Objects;

public record TriggerRule(TriggerType triggerType, CastProgram childProgram) {
    public TriggerRule {
        Objects.requireNonNull(triggerType, "triggerType");
        Objects.requireNonNull(childProgram, "childProgram");
    }
}
