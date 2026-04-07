package com.game.spellsystem.api.in;

import com.game.spellsystem.domain.value.CasterId;
import com.game.spellsystem.domain.value.WandId;

import java.util.Objects;

public record ForceReloadCommand(CasterId casterId, WandId wandId) {
    public ForceReloadCommand {
        Objects.requireNonNull(casterId, "casterId");
        Objects.requireNonNull(wandId, "wandId");
    }
}
