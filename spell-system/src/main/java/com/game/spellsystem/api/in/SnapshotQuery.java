package com.game.spellsystem.api.in;

import com.game.spellsystem.domain.value.CasterId;

import java.util.Optional;

public record SnapshotQuery(Optional<CasterId> casterIdFilter) {
}
