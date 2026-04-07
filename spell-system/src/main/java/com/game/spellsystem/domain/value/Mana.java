package com.game.spellsystem.domain.value;

public record Mana(double value) {
    public static Mana zero() {
        return new Mana(0.0);
    }
}
