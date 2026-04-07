package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.EntityId;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public record StatusEffectRequest(EntityId targetId, String effectType, Duration duration, Map<String, Double> magnitude) {
    public StatusEffectRequest {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(effectType, "effectType");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(magnitude, "magnitude");
    }
}
