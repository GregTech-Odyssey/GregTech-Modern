package com.gregtechceu.gtceu.api.recipe.content;

// Chance of a recipe content in 1/10000: base chance plus the signed change of the actual chance per overclock.
// Same meaning for inputs and outputs, equal to the boost stored in ContentList.
public record Chance(int base, int perOverclock) {

    public static Chance of(int base) {
        return new Chance(base, 0);
    }

    public Chance risePerOverclock(int step) {
        if (step < 0) throw new IllegalArgumentException("Chance step must not be negative: " + step);
        return new Chance(base, step);
    }

    public Chance dropPerOverclock(int step) {
        if (step < 0) throw new IllegalArgumentException("Chance step must not be negative: " + step);
        return new Chance(base, -step);
    }
}
