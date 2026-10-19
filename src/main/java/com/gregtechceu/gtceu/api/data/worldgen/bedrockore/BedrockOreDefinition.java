package com.gregtechceu.gtceu.api.data.worldgen.bedrockore;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.bedrock.BedrockVeinDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class BedrockOreDefinition extends BedrockVeinDefinition {

    private final int size;
    private final IntProvider yield;
    private final List<WeightedMaterial> materials;

    private BedrockOreDefinition(Builder builder) {
        super(builder);
        this.size = builder.size;
        this.yield = builder.yield;
        this.materials = builder.materials;
    }

    public static Builder builder(ResourceLocation name) {
        return new Builder(name);
    }

    public int size() {
        return size;
    }

    public IntProvider yield() {
        return yield;
    }

    public List<WeightedMaterial> materials() {
        return materials;
    }

    public static ItemStack dropOf(Material material, int count) {
        var stack = ChemicalHelper.get(TagPrefix.get(ConfigHolder.INSTANCE.machines.bedrockOreDropTagPrefix), material, count);
        if (stack.isEmpty()) stack = ChemicalHelper.get(TagPrefix.crushed, material, count);
        if (stack.isEmpty()) stack = ChemicalHelper.get(TagPrefix.gem, material, count);
        if (stack.isEmpty()) stack = ChemicalHelper.get(TagPrefix.ore, material, count);
        if (stack.isEmpty()) stack = ChemicalHelper.get(TagPrefix.dust, material, count);
        return stack;
    }

    public static final class Builder extends BedrockVeinDefinition.Builder<Builder, BedrockOreDefinition> {

        private int size;
        private IntProvider yield;
        private List<WeightedMaterial> materials;

        private Builder(ResourceLocation name) {
            super(name, "gtceu.jei.bedrock_ore." + name.getPath());
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public Builder yield(IntProvider yield) {
            this.yield = yield;
            return this;
        }

        public Builder yield(int min, int max) {
            return this.yield(UniformInt.of(min, max));
        }

        public Builder material(Material material, int amount) {
            if (this.materials == null) this.materials = new ArrayList<>();
            this.materials.add(new WeightedMaterial(material, amount));
            return this;
        }

        public Builder materials(List<WeightedMaterial> materials) {
            this.materials = materials;
            return this;
        }

        @Override
        public BedrockOreDefinition register() {
            var definition = new BedrockOreDefinition(this);
            GTRegistries.BEDROCK_ORE_DEFINITIONS.register(name, definition);
            return definition;
        }
    }
}
