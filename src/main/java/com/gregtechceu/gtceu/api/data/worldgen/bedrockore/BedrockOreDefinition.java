package com.gregtechceu.gtceu.api.data.worldgen.bedrockore;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.worldgen.BiomeWeightModifier;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.utils.RegistryUtil;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.*;

public class BedrockOreDefinition {

    private int weight; // weight value for determining which vein will appear
    private int size; // size in chunks
    private IntProvider yield;// the [minimum, maximum] yields
    private int depletionAmount; // amount of ore the vein gets drained by
    private int depletionChance; // the chance [0, 100] that the vein will deplete by 1
    private int depletedYield; // yield after the vein is depleted
    private List<WeightedMaterial> materials; // the ores which the vein contains
    private BiomeWeightModifier biomeWeightModifier; // weighting of biomes
    private List<BiomeWeightModifier> originalModifiers; // weighting of biomes
    public Set<ResourceKey<Level>> dimensionFilter; // filtering of dimensions

    public BedrockOreDefinition(ResourceLocation name, int size, int weight, IntProvider yield, int depletionAmount, int depletionChance, int depletedYield, List<WeightedMaterial> materials, List<BiomeWeightModifier> originalModifiers, Set<ResourceKey<Level>> dimensionFilter) {
        this(weight, size, yield, depletionAmount, depletionChance, depletedYield, materials, originalModifiers, dimensionFilter);
        GTRegistries.BEDROCK_ORE_DEFINITIONS.register(name, this);
    }

    public BedrockOreDefinition(int weight, int size, IntProvider yield, int depletionAmount, int depletionChance, int depletedYield, List<WeightedMaterial> materials, List<BiomeWeightModifier> originalModifiers, Set<ResourceKey<Level>> dimensionFilter) {
        this.weight = weight;
        this.size = size;
        this.yield = yield;
        this.depletionAmount = depletionAmount;
        this.depletionChance = depletionChance;
        this.depletedYield = depletedYield;
        this.materials = materials;
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

    public IntList getAllChances() {
        return IntArrayList.toList(materials().stream().mapToInt(WeightedMaterial::weight));
    }

    public List<Material> getAllMaterials() {
        return materials().stream().map(WeightedMaterial::material).toList();
    }

    public static Builder builder(ResourceLocation name) {
        return new Builder(name);
    }

    public static class Builder {

        private final ResourceLocation name;
        private int weight; // weight value for determining which vein will appear
        private int size; // size of the vein, in chunks.
        private IntProvider yield;// the [minimum, maximum) yields
        private int depletionAmount; // amount of fluid the vein gets drained by
        private int depletionChance = 1; // the chance [0, 100] that the vein will deplete by 1
        private int depletedYield; // yield after the vein is depleted
        private List<WeightedMaterial> materials; // the ores which the vein contains
        private Set<ResourceKey<Level>> dimensions;
        private final List<BiomeWeightModifier> biomes = new LinkedList<>();

        private Builder(ResourceLocation name) {
            this.name = name;
        }

        public Builder copy(ResourceLocation name) {
            var copied = new Builder(name);
            copied.weight = weight;
            copied.yield = yield;
            copied.depletionAmount = depletionAmount;
            copied.depletionChance = depletionChance;
            copied.depletedYield = depletedYield;
            copied.materials = materials;
            return copied;
        }

        public Builder material(Material material, int amount) {
            if (this.materials == null) this.materials = new ArrayList<>();
            this.materials.add(new WeightedMaterial(material, amount));
            return this;
        }

        public Builder yield(int min, int max) {
            return this.yield(UniformInt.of(min, max));
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
            return this.dimensions(new HashSet<>(RegistryUtil.resolveResourceKeys(Registries.DIMENSION, dimensions)));
        }

        public BedrockOreDefinition register() {
            var definition = new BedrockOreDefinition(weight, size, yield, depletionAmount, depletionChance, depletedYield, materials, biomes, dimensions);
            GTRegistries.BEDROCK_ORE_DEFINITIONS.registerOrOverride(name, definition);
            return definition;
        }

        /**
         * @return {@code this}.
         */
        public BedrockOreDefinition.Builder weight(final int weight) {
            this.weight = weight;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public BedrockOreDefinition.Builder size(final int size) {
            this.size = size;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public BedrockOreDefinition.Builder yield(final IntProvider yield) {
            this.yield = yield;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public BedrockOreDefinition.Builder depletionAmount(final int depletionAmount) {
            this.depletionAmount = depletionAmount;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public BedrockOreDefinition.Builder depletionChance(final int depletionChance) {
            this.depletionChance = depletionChance;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public BedrockOreDefinition.Builder depletedYield(final int depletedYield) {
            this.depletedYield = depletedYield;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public BedrockOreDefinition.Builder materials(final List<WeightedMaterial> materials) {
            this.materials = materials;
            return this;
        }
    }

    public int weight() {
        return this.weight;
    }

    /**
     * @return {@code this}.
     */
    public BedrockOreDefinition weight(final int weight) {
        this.weight = weight;
        return this;
    }

    public int size() {
        return this.size;
    }

    /**
     * @return {@code this}.
     */
    public BedrockOreDefinition size(final int size) {
        this.size = size;
        return this;
    }

    public IntProvider yield() {
        return this.yield;
    }

    /**
     * @return {@code this}.
     */
    public BedrockOreDefinition yield(final IntProvider yield) {
        this.yield = yield;
        return this;
    }

    public int depletionAmount() {
        return this.depletionAmount;
    }

    /**
     * @return {@code this}.
     */
    public BedrockOreDefinition depletionAmount(final int depletionAmount) {
        this.depletionAmount = depletionAmount;
        return this;
    }

    public int depletionChance() {
        return this.depletionChance;
    }

    /**
     * @return {@code this}.
     */
    public BedrockOreDefinition depletionChance(final int depletionChance) {
        this.depletionChance = depletionChance;
        return this;
    }

    public int depletedYield() {
        return this.depletedYield;
    }

    /**
     * @return {@code this}.
     */
    public BedrockOreDefinition depletedYield(final int depletedYield) {
        this.depletedYield = depletedYield;
        return this;
    }

    public List<WeightedMaterial> materials() {
        return this.materials;
    }

    /**
     * @return {@code this}.
     */
    public BedrockOreDefinition materials(final List<WeightedMaterial> materials) {
        this.materials = materials;
        return this;
    }

    public BiomeWeightModifier biomeWeightModifier() {
        return this.biomeWeightModifier;
    }

    public Set<ResourceKey<Level>> dimensionFilter() {
        return this.dimensionFilter;
    }

    /**
     * @return {@code this}.
     */
    public BedrockOreDefinition dimensionFilter(final Set<ResourceKey<Level>> dimensionFilter) {
        this.dimensionFilter = dimensionFilter;
        return this;
    }
}
