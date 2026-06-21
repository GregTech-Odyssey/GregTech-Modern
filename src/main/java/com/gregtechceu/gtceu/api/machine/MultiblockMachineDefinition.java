package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.registry.registrate.MultiblockMachineBuilder;
import com.gregtechceu.gtceu.utils.memoization.GTMemoizer;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
    @Getter
    protected Supplier<BlockPattern>[] subPatternFactory;
    @Getter
    @Setter
    protected Supplier<List<MultiblockShapeInfo>> shapes;
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

    protected MultiblockMachineDefinition(ResourceLocation id) {
        super(id);
    }

    public static MultiblockMachineDefinition createDefinition(ResourceLocation id) {
        return new MultiblockMachineDefinition(id);
    }

    public List<MultiblockShapeInfo> getMatchingShapes() {
        var designs = shapes.get();
        if (!designs.isEmpty()) return designs;
        var list = new ArrayList<MultiblockShapeInfo>();
        for (var factory : patternFactory) {
            var structurePattern = factory.get();
            int[][] aisleRepetitions = structurePattern.aisleRepetitions;
            list.addAll(repetitionDFS(structurePattern, new ArrayList<>(), aisleRepetitions, new IntArrayList()));
        }
        return list;
    }

    private List<MultiblockShapeInfo> repetitionDFS(BlockPattern pattern, List<MultiblockShapeInfo> pages, int[][] aisleRepetitions, IntArrayList repetitionStack) {
        if (repetitionStack.size() == aisleRepetitions.length) {
            int[] repetition = new int[repetitionStack.size()];
            for (int i = 0; i < repetitionStack.size(); i++) {
                repetition[i] = repetitionStack.getInt(i);
            }
            pages.add(new MultiblockShapeInfo(() -> pattern, pattern.getPreview(repetition)));
        } else {
            for (int i = aisleRepetitions[repetitionStack.size()][0]; i <= aisleRepetitions[repetitionStack.size()][1]; i++) {
                repetitionStack.push(i);
                repetitionDFS(pattern, pages, aisleRepetitions, repetitionStack);
                repetitionStack.popInt();
            }
        }
        return pages;
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

    public void setSubPatternFactory(final List<Function<MultiblockMachineDefinition, BlockPattern>> subPatternFactory) {
        this.subPatternFactory = subPatternFactory.stream().map(p -> GTMemoizer.memoize(() -> {
            var pattern = p.apply(this);
            pattern.isSubPattern = true;
            return pattern;
        })).toArray(Supplier[]::new);
    }

    public void setRecoveryItems(@Nullable final MultiblockMachineBuilder.MufflerProductionGenerator recoveryItems) {
        this.recoveryItems = recoveryItems;
    }

    @Nullable
    public MultiblockMachineBuilder.MufflerProductionGenerator getRecoveryItems() {
        return this.recoveryItems;
    }
}
