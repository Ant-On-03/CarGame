package com.game.spellsystem.domain.model;

public sealed interface DomainEvent permits CastStartedEvent, CardDrawnEvent, ManaSpentEvent, ProjectileRequestedEvent, CastFailedEvent, WandRechargedEvent {
}
