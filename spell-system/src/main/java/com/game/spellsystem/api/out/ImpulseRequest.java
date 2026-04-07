package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.EntityId;
import com.game.spellsystem.domain.value.Vector2d;
import com.game.spellsystem.domain.value.WorldPoint;

import java.util.Objects;

public record ImpulseRequest(EntityId targetId, Vector2d impulse, WorldPoint atPoint) {
    public ImpulseRequest {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(impulse, "impulse");
        Objects.requireNonNull(atPoint, "atPoint");
    }
}
