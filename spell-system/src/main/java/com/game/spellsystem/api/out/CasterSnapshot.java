package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.Angle;
import com.game.spellsystem.domain.value.CasterId;
import com.game.spellsystem.domain.value.Vector2d;
import com.game.spellsystem.domain.value.WorldPoint;

import java.util.Map;
import java.util.Objects;

public record CasterSnapshot(
        CasterId casterId,
        WorldPoint position,
        Angle aimAngle,
        Vector2d velocity,
        String faction,
        Map<String, Double> numericStats
) {
    public CasterSnapshot {
        Objects.requireNonNull(casterId, "casterId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(aimAngle, "aimAngle");
        Objects.requireNonNull(velocity, "velocity");
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(numericStats, "numericStats");
    }
}
