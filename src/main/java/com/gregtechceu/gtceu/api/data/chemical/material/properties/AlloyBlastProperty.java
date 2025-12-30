package com.gregtechceu.gtceu.api.data.chemical.material.properties;

import com.gregtechceu.gtceu.data.recipe.misc.alloyblast.AlloyBlastRecipeProducer;

import net.minecraft.world.level.material.Fluid;

import com.google.common.base.Preconditions;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class AlloyBlastProperty implements IMaterialProperty {

    /**
     * Internal material fluid field
     */
    private Supplier<? extends Fluid> fluidSupplier;
    @Getter
    private int temperature;
    @NotNull
    private AlloyBlastRecipeProducer recipeProducer = AlloyBlastRecipeProducer.DEFAULT_PRODUCER;

    public AlloyBlastProperty(int temperature) {
        this.temperature = temperature;
    }

    @Override
    public void verifyProperty(MaterialProperties materialProperties) {
        materialProperties.ensureSet(PropertyKey.BLAST);
        materialProperties.ensureSet(PropertyKey.FLUID);
        this.temperature = materialProperties.getProperty(PropertyKey.BLAST).getBlastTemperature();
    }

    /**
     * internal usage only
     */
    public void setFluid(@NotNull Supplier<? extends Fluid> materialFluid) {
        Preconditions.checkNotNull(materialFluid);
        this.fluidSupplier = materialFluid;
    }

    public Fluid getFluid() {
        return fluidSupplier.get();
    }

    public void setTemperature(int fluidTemperature) {
        Preconditions.checkArgument(fluidTemperature > 0, "Invalid temperature");
        this.temperature = fluidTemperature;
    }

    @NotNull
    public AlloyBlastRecipeProducer getRecipeProducer() {
        return this.recipeProducer;
    }

    public void setRecipeProducer(@NotNull final AlloyBlastRecipeProducer recipeProducer) {
        this.recipeProducer = recipeProducer;
    }
}
