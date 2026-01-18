package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public class BiomeCondition extends RecipeCondition {

    public static final BiomeCondition INSTANCE = new BiomeCondition();
    private ResourceKey<Biome> biome = ResourceKey.create(Registries.BIOME, new ResourceLocation("dummy"));

    public BiomeCondition(boolean isReverse, ResourceKey<Biome> biome) {
        super(isReverse);
        this.biome = biome;
    }

    public BiomeCondition(ResourceKey<Biome> biome) {
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
    public boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
        Level level = recipeLogic.machine.self().getLevel();
        if (level == null) return false;
        Holder<Biome> biome = level.getBiome(recipeLogic.machine.self().getPos());
        return biome.is(this.biome);
    }

    public BiomeCondition() {}
}
