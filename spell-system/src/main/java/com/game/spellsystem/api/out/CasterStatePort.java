package com.game.spellsystem.api.out;

import com.game.spellsystem.domain.value.CasterId;
import com.game.spellsystem.domain.value.WandId;

import java.util.Optional;

public interface CasterStatePort {
    CasterSnapshot getCasterSnapshot(CasterId casterId);

    Optional<WandExternalModifiers> getExternalWandModifiers(CasterId casterId, WandId wandId);
}
