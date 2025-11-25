package com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid;

import com.gregtechceu.gtceu.api.data.worldgen.BiomeWeightModifier;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.utils.RegistryUtil;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.Fluid;

import com.fast.fastcollection.OpenCacheHashSet;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class BedrockFluidDefinition {

    @Getter
    @Setter
    private int weight; // weight value for determining which vein will appear
    @Getter
    @Setter
    private int minimumYield;
    @Getter
    @Setter
    private int maximumYield;// the [minimum, maximum) yields
    @Getter
    @Setter
    private int depletionAmount; // amount of fluid the vein gets drained by
    @Getter
    @Setter
    private int depletionChance; // the chance [0, 100] that the vein will deplete by 1
    @Getter
    @Setter
    private int depletedYield; // yield after the vein is depleted
    @Getter
    @Setter
    private Supplier<Fluid> storedFluid; // the fluid which the vein contains
    @Getter
    private BiomeWeightModifier biomeWeightModifier; // weighting of biomes
    private List<BiomeWeightModifier> originalModifiers; // weighting of biomes
    @Getter
    @Setter
    public Set<ResourceKey<Level>> dimensionFilter; // filtering of dimensions

    public BedrockFluidDefinition(ResourceLocation name, int weight, int minimumYield, int maximumYield, int depletionAmount, int depletionChance, int depletedYield, Supplier<Fluid> storedFluid, List<BiomeWeightModifier> originalModifiers, Set<ResourceKey<Level>> dimensionFilter) {
        this(weight, minimumYield, maximumYield, depletionAmount, depletionChance, depletedYield, storedFluid, originalModifiers, dimensionFilter);
        GTRegistries.BEDROCK_FLUID_DEFINITIONS.register(name, this);
    }

    public BedrockFluidDefinition(int weight, int minimumYield, int maximumYield, int depletionAmount, int depletionChance, int depletedYield, Supplier<Fluid> storedFluid, List<BiomeWeightModifier> originalModifiers, Set<ResourceKey<Level>> dimensionFilter) {
        this.weight = weight;
        this.minimumYield = minimumYield;
        this.maximumYield = maximumYield;
        this.depletionAmount = depletionAmount;
        this.depletionChance = depletionChance;
        this.depletedYield = depletedYield;
        this.storedFluid = storedFluid;
        this.originalModifiers = originalModifiers;
        this.biomeWeightModifier = new BiomeWeightModifier(() -> HolderSet.direct(originalModifiers.stream().flatMap(mod -> mod.biomes.get().stream()).toList()), originalModifiers.stream().mapToInt(mod -> mod.addedWeight).sum()) {

            @Override
            public int applyAsInt(Holder<Biome> biome) {
                int mod = 0;
                for (var modifier : originalModifiers) {
                    if (modifier.biomes.get().contains(biome)) {
                        mod += modifier.applyAsInt(biome);
                    }
                }
                return mod;
            }
        };
        this.dimensionFilter = dimensionFilter;
    }

    public void setOriginalModifiers(List<BiomeWeightModifier> modifiers) {
        this.originalModifiers = modifiers;
        this.biomeWeightModifier = new BiomeWeightModifier(() -> HolderSet.direct(originalModifiers.stream().flatMap(mod -> mod.biomes.get().stream()).toList()), originalModifiers.stream().mapToInt(mod -> mod.addedWeight).sum()) {

            @Override
            public int applyAsInt(Holder<Biome> biome) {
                int mod = 0;
                for (var modifier : originalModifiers) {
                    if (modifier.biomes.get().contains(biome)) {
                        mod += modifier.applyAsInt(biome);
                    }
                }
                return mod;
            }
        };
    }

    public static Builder builder(ResourceLocation name) {
        return new Builder(name);
    }

    public static class Builder {

        private final ResourceLocation name;
        private int weight; // weight value for determining which vein will appear
        private int minimumYield;
        private int maximumYield;// the [minimum, maximum) yields
        private int depletionAmount; // amount of fluid the vein gets drained by
        private int depletionChance = 1; // the chance [0, 100] that the vein will deplete by 1
        private int depletedYield; // yield after the vein is depleted
        private Supplier<Fluid> fluid; // the fluid which the vein contains
        private Set<ResourceKey<Level>> dimensions;
        private final List<BiomeWeightModifier> biomes = new LinkedList<>();

        private Builder(ResourceLocation name) {
            this.name = name;
        }

        public Builder copy(ResourceLocation name) {
            var copied = new Builder(name);
            copied.weight = weight;
            copied.minimumYield = minimumYield;
            copied.maximumYield = maximumYield;
            copied.depletionAmount = depletionAmount;
            copied.depletionChance = depletionChance;
            copied.depletedYield = depletedYield;
            copied.fluid = fluid;
            return copied;
        }

        public Builder yield(int min, int max) {
            return minimumYield(min).maximumYield(max);
        }

        public Builder biomes(int weight, TagKey<Biome> biomes) {
            this.biomes.add(new BiomeWeightModifier(() -> GTRegistries.builtinRegistry().registryOrThrow(Registries.BIOME).getOrCreateTag(biomes), weight));
            return this;
        }

        @SafeVarargs
        public final Builder biomes(int weight, ResourceKey<Biome>... biomes) {
            this.biomes.add(new BiomeWeightModifier(() -> HolderSet.direct(GTRegistries.builtinRegistry().registryOrThrow(Registries.BIOME)::getHolderOrThrow, biomes), weight));
            return this;
        }

        public Builder biomes(int weight, HolderSet<Biome> biomes) {
            this.biomes.add(new BiomeWeightModifier(() -> biomes, weight));
            return this;
        }

        public Builder dimensions(Set<ResourceKey<Level>> dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public Builder dimensions(String... dimensions) {
            return this.dimensions(new OpenCacheHashSet<>(RegistryUtil.resolveResourceKeys(Registries.DIMENSION, dimensions)));
        }

        public BedrockFluidDefinition register() {
            var definition = new BedrockFluidDefinition(weight, minimumYield, maximumYield, depletionAmount, depletionChance, depletedYield, fluid, biomes, dimensions);
            GTRegistries.BEDROCK_FLUID_DEFINITIONS.registerOrOverride(name, definition);
            return definition;
        }

        /**
         * @return {@code this}.
         */
        public BedrockFluidDefinition.Builder weight(final int weight) {
            this.weight = weight;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public BedrockFluidDefinition.Builder minimumYield(final int minimumYield) {
            this.minimumYield = minimumYield;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public BedrockFluidDefinition.Builder maximumYield(final int maximumYield) {
            this.maximumYield = maximumYield;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public BedrockFluidDefinition.Builder depletionAmount(final int depletionAmount) {
            this.depletionAmount = depletionAmount;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public BedrockFluidDefinition.Builder depletionChance(final int depletionChance) {
            this.depletionChance = depletionChance;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public BedrockFluidDefinition.Builder depletedYield(final int depletedYield) {
            this.depletedYield = depletedYield;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public BedrockFluidDefinition.Builder fluid(final Supplier<Fluid> fluid) {
            this.fluid = fluid;
            return this;
        }
    }
}
