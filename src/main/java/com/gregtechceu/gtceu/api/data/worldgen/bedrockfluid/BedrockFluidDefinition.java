package com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid;

import com.gregtechceu.gtceu.api.data.worldgen.bedrock.BedrockVeinDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

import java.util.function.Supplier;

public final class BedrockFluidDefinition extends BedrockVeinDefinition {

    private final int minimumYield;
    private final int maximumYield;
    private final Supplier<Fluid> storedFluid;

    private BedrockFluidDefinition(Builder builder) {
        super(builder);
        this.minimumYield = builder.minimumYield;
        this.maximumYield = builder.maximumYield;
        this.storedFluid = builder.fluid;
    }

    public static Builder builder(ResourceLocation name) {
        return new Builder(name);
    }

    public int minimumYield() {
        return minimumYield;
    }

    public int maximumYield() {
        return maximumYield;
    }

    public Fluid fluid() {
        return storedFluid.get();
    }

    public static final class Builder extends BedrockVeinDefinition.Builder<Builder, BedrockFluidDefinition> {

        private int minimumYield;
        private int maximumYield;
        private Supplier<Fluid> fluid;

        private Builder(ResourceLocation name) {
            super(name, "gtceu.jei.bedrock_fluid." + name.getPath());
        }

        public Builder yield(int min, int max) {
            this.minimumYield = min;
            this.maximumYield = max;
            return this;
        }

        public Builder fluid(Supplier<Fluid> fluid) {
            this.fluid = fluid;
            return this;
        }

        @Override
        public BedrockFluidDefinition register() {
            var definition = new BedrockFluidDefinition(this);
            GTRegistries.BEDROCK_FLUID_DEFINITIONS.register(name, definition);
            return definition;
        }
    }
}
