package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblockpro.MachineProtocol;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.registry.registrate.MultiblockMachineBuilder;
import com.gregtechceu.gtceu.utils.memoization.GTMemoizer;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class MultiblockMachineDefinition extends MachineDefinition {

    protected int checkPriority;
    @Getter
    @Setter
    protected boolean generator;

    @Getter
    protected Supplier<BlockPattern>[] patternFactory;
    /**
     * Set this to false only if your multiblock is set up such that it could have a wall-shared controller.
     * -- SETTER --
     * Set this to false only if your multiblock is set up such that it could have a wall-shared controller.
     * -- GETTER --
     * Set this to false only if your multiblock is set up such that it could have a wall-shared controller.
     * 
     * 
     */
    @Getter
    @Setter
    protected boolean allowFlip;
    @Getter
    @Setter
    protected boolean renderXEIPreview;
    @Nullable
    protected MultiblockMachineBuilder.MufflerProductionGenerator recoveryItems;
    @Getter
    @Setter
    protected TriFunction<IMultiController, IMultiPart, Direction, BlockState> partAppearance;
    @Getter
    @Setter
    protected BiConsumer<IMultiController, List<Component>> additionalDisplay;
    @Getter
    @Setter
    protected List<MachineProtocol> mountedOn = List.of();

    protected MultiblockMachineDefinition(ResourceLocation id) {
        super(id);
    }

    public static MultiblockMachineDefinition createDefinition(ResourceLocation id) {
        return new MultiblockMachineDefinition(id);
    }

    public int checkPriority() {
        return checkPriority;
    }

    public void setCheckPriority(final int checkPriority) {
        if (this.checkPriority == 0) {
            this.checkPriority = checkPriority;
        }
    }

    public void setPatternFactory(final List<Function<MultiblockMachineDefinition, BlockPattern>> patternFactory) {
        this.patternFactory = patternFactory.stream().map(p -> GTMemoizer.memoize(() -> p.apply(this))).toArray(Supplier[]::new);
    }

    public void setRecoveryItems(@Nullable final MultiblockMachineBuilder.MufflerProductionGenerator recoveryItems) {
        this.recoveryItems = recoveryItems;
    }

    @Nullable
    public MultiblockMachineBuilder.MufflerProductionGenerator getRecoveryItems() {
        return this.recoveryItems;
    }
}
