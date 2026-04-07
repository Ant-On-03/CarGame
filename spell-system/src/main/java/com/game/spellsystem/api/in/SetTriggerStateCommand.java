package com.game.spellsystem.api.in;

import com.game.spellsystem.domain.value.CasterId;
import com.game.spellsystem.domain.value.WandId;

import java.util.Objects;

public record SetTriggerStateCommand(CasterId casterId, WandId wandId, TriggerState state) {
    public SetTriggerStateCommand {
        Objects.requireNonNull(casterId, "casterId");
        Objects.requireNonNull(wandId, "wandId");
        Objects.requireNonNull(state, "state");
    }
}
