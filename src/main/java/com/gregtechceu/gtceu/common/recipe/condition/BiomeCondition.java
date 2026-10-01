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

import org.jetbrains.annotations.Nullable;

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
        var name = Component.translatableWithFallback(biome.location().toLanguageKey("biome"), biome.location().toString());
        return Component.translatable(isReverse ? "recipe.condition.biome.reverse.tooltip" : "recipe.condition.biome.tooltip", name);
    }

    @Override
    public @Nullable Component describeCurrent(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        Level level = holder.self().getLevel();
        if (level == null) return null;
        var key = level.getBiome(holder.self().getPos()).unwrapKey().orElse(null);
        if (key == null) return null;
        var id = key.location();
        return Component.translatable("gtceu.issue.current.biome", Component.translatableWithFallback(id.toLanguageKey("biome"), id.toString()));
    }

    @Override
    public boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        Level level = holder.self().getLevel();
        if (level == null) return false;
        Holder<Biome> biome = level.getBiome(holder.self().getPos());
        return biome.is(this.biome);
    }
}
