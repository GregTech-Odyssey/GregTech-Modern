package com.gregtechceu.gtceu.api.recipe.condition;

import com.gregtechceu.gtceu.api.recipe.RecipeCondition;

import com.mojang.serialization.Codec;

public class RecipeConditionType<T extends RecipeCondition> {

    public final ConditionFactory<T> factory;
    public final Codec<T> codec;

    @FunctionalInterface
    public interface ConditionFactory<T extends RecipeCondition> {

        T createDefault();
    }

    public RecipeConditionType(final ConditionFactory<T> factory, final Codec<T> codec) {
        this.factory = factory;
        this.codec = codec;
    }

    public Codec<T> getCodec() {
        return this.codec;
    }
}
