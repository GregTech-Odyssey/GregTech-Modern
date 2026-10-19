package com.gregtechceu.gtceu.api.data.worldgen.bedrock;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.registry.GTRegistry;

import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Declares one kind of bedrock vein: where its definitions live, how veins are laid out over chunks,
 * how entries are stored and how much each drill tier multiplies the per-operation yield.
 */
public final class BedrockVeinKind<D extends BedrockVeinDefinition> {

    private static final List<BedrockVeinKind<?>> ALL = new ObjectArrayList<>();

    public static final BedrockVeinKind<BedrockOreDefinition> ORE = builder("gtceu_bedrock_ore", GTRegistries.BEDROCK_ORE_DEFINITIONS)
            .layout(new SpreadVeinLayout(3))
            .legacyStorage("pos", "data", "oreYield")
            .rig(GTValues.MV, 1, 1)
            .rig(GTValues.HV, 4, 2)
            .rig(GTValues.EV, 16, 8)
            .register();

    public static final BedrockVeinKind<BedrockFluidDefinition> FLUID = builder("gtceu_bedrock_fluid", GTRegistries.BEDROCK_FLUID_DEFINITIONS)
            .layout(new ChunkVeinLayout(8))
            .legacyStorage("p", "d", "fluidYield")
            .rig(GTValues.MV, 1, 1)
            .rig(GTValues.HV, 16, 2)
            .rig(GTValues.EV, 64, 8)
            .register();

    private final String id;
    private final GTRegistry.RL<D> registry;
    private final BedrockVeinLayout<D> layout;
    private final String posKey;
    private final String dataKey;
    private final String yieldKey;
    private final int[] rigMultipliers;
    private final int[] depletionChances;

    private BedrockVeinKind(Builder<D> builder) {
        this.id = builder.id;
        this.registry = builder.registry;
        this.layout = builder.layout;
        this.posKey = builder.posKey;
        this.dataKey = builder.dataKey;
        this.yieldKey = builder.yieldKey;
        this.rigMultipliers = builder.rigMultipliers;
        this.depletionChances = builder.depletionChances;
    }

    public static List<BedrockVeinKind<?>> all() {
        return Collections.unmodifiableList(ALL);
    }

    public BedrockVeinSavedData<D> data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> new BedrockVeinSavedData<>(this, level, tag), () -> new BedrockVeinSavedData<>(this, level), id);
    }

    public int rigMultiplier(int tier) {
        return tier >= 0 && tier < rigMultipliers.length ? rigMultipliers[tier] : 1;
    }

    /**
     * A finished operation of a rig of this tier depletes the vein with a chance of one in this value.
     */
    public int depletionChance(int tier) {
        return tier >= 0 && tier < depletionChances.length ? depletionChances[tier] : 1;
    }

    public GTRegistry.RL<D> registry() {
        return registry;
    }

    BedrockVeinLayout<D> layout() {
        return layout;
    }

    @Deprecated(since = "0.6.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
    String legacyPosKey() {
        return posKey;
    }

    @Deprecated(since = "0.6.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
    String legacyDataKey() {
        return dataKey;
    }

    @Deprecated(since = "0.6.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
    String legacyYieldKey() {
        return yieldKey;
    }

    @Override
    public String toString() {
        return id;
    }

    private static <D extends BedrockVeinDefinition> Builder<D> builder(String id, GTRegistry.RL<D> registry) {
        return new Builder<>(id, registry);
    }

    private static final class Builder<D extends BedrockVeinDefinition> {

        private final String id;
        private final GTRegistry.RL<D> registry;
        private BedrockVeinLayout<D> layout;
        private String posKey;
        private String dataKey;
        private String yieldKey;
        private final int[] rigMultipliers = new int[GTValues.TIER_COUNT];
        private final int[] depletionChances = new int[GTValues.TIER_COUNT];

        private Builder(String id, GTRegistry.RL<D> registry) {
            this.id = id;
            this.registry = registry;
            Arrays.fill(rigMultipliers, 1);
            Arrays.fill(depletionChances, 1);
        }

        private Builder<D> layout(BedrockVeinLayout<D> layout) {
            this.layout = layout;
            return this;
        }

        @Deprecated(since = "0.6.0", forRemoval = true)
        @ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
        private Builder<D> legacyStorage(String posKey, String dataKey, String yieldKey) {
            this.posKey = posKey;
            this.dataKey = dataKey;
            this.yieldKey = yieldKey;
            return this;
        }

        private Builder<D> rig(int tier, int multiplier, int depletionChance) {
            rigMultipliers[tier] = multiplier;
            depletionChances[tier] = depletionChance;
            return this;
        }

        private BedrockVeinKind<D> register() {
            var kind = new BedrockVeinKind<>(this);
            ALL.add(kind);
            return kind;
        }
    }
}
