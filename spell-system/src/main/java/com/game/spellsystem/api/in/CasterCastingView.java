package com.game.spellsystem.api.in;

import com.game.spellsystem.domain.value.CasterId;
import com.game.spellsystem.domain.value.WandId;

import java.util.Objects;
import java.util.Optional;

public record CasterCastingView(CasterId casterId, Optional<WandId> activeWandId, TriggerState triggerState) {
    public CasterCastingView {
        Objects.requireNonNull(casterId, "casterId");
        Objects.requireNonNull(activeWandId, "activeWandId");
        Objects.requireNonNull(triggerState, "triggerState");
    }
}
