package com.game.spellsystem.api.in;

import com.game.spellsystem.domain.model.WandDefinition;
import com.game.spellsystem.domain.value.CasterId;

import java.util.Objects;

public record EquipWandCommand(CasterId casterId, WandDefinition wandDefinition) {
    public EquipWandCommand {
        Objects.requireNonNull(casterId, "casterId");
        Objects.requireNonNull(wandDefinition, "wandDefinition");
    }
}
