package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public class BiomeCondition extends RecipeCondition {

    public final ResourceKey<Biome> biome;

    public BiomeCondition(boolean isReverse, ResourceKey<Biome> biome) {
        super(isReverse);
        this.biome = biome;
    }

    @Override
    public boolean isOr() {
        return true;
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.biome.tooltip", Component.translatableWithFallback(biome.location().toLanguageKey("biome"), biome.location().toString()));
    }

    @Override
    public boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        Level level = holder.self().getLevel();
        if (level == null) return false;
        Holder<Biome> biome = level.getBiome(holder.self().getPos());
        return biome.is(this.biome);
    }
}
