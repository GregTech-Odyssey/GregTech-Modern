package com.gregtechceu.gtceu.api.data.worldgen.bedrock;

import com.gregtechceu.gtceu.config.ConfigHolder;

import org.jetbrains.annotations.Nullable;

public final class BedrockVeinEntry<D extends BedrockVeinDefinition> {

    public static final int MAX_OPERATIONS = 100_000;
    @SuppressWarnings("rawtypes")
    private static final BedrockVeinEntry EMPTY = new BedrockVeinEntry<>(null, 0, MAX_OPERATIONS);

    @Nullable
    private final D definition;
    private final int yield;
    private int operationsRemaining;

    BedrockVeinEntry(@Nullable D definition, int yield, int operationsRemaining) {
        this.definition = definition;
        this.yield = yield;
        this.operationsRemaining = operationsRemaining;
    }

    @SuppressWarnings("unchecked")
    static <D extends BedrockVeinDefinition> BedrockVeinEntry<D> empty() {
        return EMPTY;
    }

    @Nullable
    public D definition() {
        return definition;
    }

    public int yield() {
        return yield;
    }

    public int operationsRemaining() {
        return operationsRemaining;
    }

    public int remainingPercent() {
        return 100 * operationsRemaining / MAX_OPERATIONS;
    }

    public int yieldPerOperation() {
        if (definition == null) return 0;
        return Math.max(definition.depletedYield(), (int) ((long) yield * operationsRemaining / MAX_OPERATIONS));
    }

    boolean changeOperations(int delta) {
        if (definition == null || ConfigHolder.INSTANCE.worldgen.oreVeins.infiniteBedrockOresFluids) return false;
        int next = Math.max(0, operationsRemaining + delta);
        if (next == operationsRemaining) return false;
        operationsRemaining = next;
        return true;
    }
}
