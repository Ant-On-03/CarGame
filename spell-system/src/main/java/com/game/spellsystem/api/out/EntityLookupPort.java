package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.EntityId;

import java.util.Optional;

public interface EntityLookupPort {
    Optional<EntitySnapshot> getEntity(EntityId entityId);
}
