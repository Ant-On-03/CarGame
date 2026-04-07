package com.game.spellsystem.api.out;

import java.util.Map;
import java.util.Objects;

public record GameplayEvent(String eventType, Map<String, String> attributes) {
    public GameplayEvent {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(attributes, "attributes");
    }
}
