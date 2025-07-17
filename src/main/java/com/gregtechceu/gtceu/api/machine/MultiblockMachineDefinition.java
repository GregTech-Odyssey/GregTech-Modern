package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class MultiblockMachineDefinition extends MachineDefinition {

    private boolean generator;

    private Supplier<BlockPattern> patternFactory;
    private Supplier<List<MultiblockShapeInfo>> shapes;
    /**
     * Set this to false only if your multiblock is set up such that it could have a wall-shared controller.
     */
    private boolean allowFlip;
    private boolean renderXEIPreview;
    @Nullable
    private Supplier<ItemStack[]> recoveryItems;
    private Function<MultiblockControllerMachine, Comparator<IMultiPart>> partSorter;
    private TriFunction<IMultiController, IMultiPart, Direction, BlockState> partAppearance;
    private BiConsumer<IMultiController, List<Component>> additionalDisplay;

    protected MultiblockMachineDefinition(ResourceLocation id) {
        super(id);
    }

    public static MultiblockMachineDefinition createDefinition(ResourceLocation id) {
        return new MultiblockMachineDefinition(id);
    }

    public List<MultiblockShapeInfo> getMatchingShapes() {
        var designs = shapes.get();
        if (!designs.isEmpty()) return designs;
        var structurePattern = patternFactory.get();
        int[][] aisleRepetitions = structurePattern.aisleRepetitions;
        return repetitionDFS(structurePattern, new ArrayList<>(), aisleRepetitions, new IntArrayList());
    }

    private List<MultiblockShapeInfo> repetitionDFS(BlockPattern pattern, List<MultiblockShapeInfo> pages, int[][] aisleRepetitions, IntArrayList repetitionStack) {
        if (repetitionStack.size() == aisleRepetitions.length) {
            int[] repetition = new int[repetitionStack.size()];
            for (int i = 0; i < repetitionStack.size(); i++) {
                repetition[i] = repetitionStack.getInt(i);
            }
            pages.add(new MultiblockShapeInfo(pattern.getPreview(repetition)));
        } else {
            for (int i = aisleRepetitions[repetitionStack.size()][0]; i <= aisleRepetitions[repetitionStack.size()][1]; i++) {
                repetitionStack.push(i);
                repetitionDFS(pattern, pages, aisleRepetitions, repetitionStack);
                repetitionStack.popInt();
            }
        }
        return pages;
    }

    public boolean isGenerator() {
        return this.generator;
    }

    public void setGenerator(final boolean generator) {
        this.generator = generator;
    }

    public void setPatternFactory(final Supplier<BlockPattern> patternFactory) {
        if (patternFactory == null) {
            throw new NullPointerException("patternFactory is marked non-null but is null");
        }
        this.patternFactory = patternFactory;
    }

    public Supplier<BlockPattern> getPatternFactory() {
        return this.patternFactory;
    }

    public void setShapes(final Supplier<List<MultiblockShapeInfo>> shapes) {
        this.shapes = shapes;
    }

    public Supplier<List<MultiblockShapeInfo>> getShapes() {
        return this.shapes;
    }

    /**
     * Set this to false only if your multiblock is set up such that it could have a wall-shared controller.
     */
    public boolean isAllowFlip() {
        return this.allowFlip;
    }

    /**
     * Set this to false only if your multiblock is set up such that it could have a wall-shared controller.
     */
    public void setAllowFlip(final boolean allowFlip) {
        this.allowFlip = allowFlip;
    }

    public boolean isRenderXEIPreview() {
        return this.renderXEIPreview;
    }

    public void setRenderXEIPreview(final boolean renderXEIPreview) {
        this.renderXEIPreview = renderXEIPreview;
    }

    public void setRecoveryItems(@Nullable final Supplier<ItemStack[]> recoveryItems) {
        this.recoveryItems = recoveryItems;
    }

    @Nullable
    public Supplier<ItemStack[]> getRecoveryItems() {
        return this.recoveryItems;
    }

    public void setPartSorter(final Function<MultiblockControllerMachine, Comparator<IMultiPart>> partSorter) {
        this.partSorter = partSorter;
    }

    public Function<MultiblockControllerMachine, Comparator<IMultiPart>> getPartSorter() {
        return this.partSorter;
    }

    public TriFunction<IMultiController, IMultiPart, Direction, BlockState> getPartAppearance() {
        return this.partAppearance;
    }

    public void setPartAppearance(final TriFunction<IMultiController, IMultiPart, Direction, BlockState> partAppearance) {
        this.partAppearance = partAppearance;
    }

    public BiConsumer<IMultiController, List<Component>> getAdditionalDisplay() {
        return this.additionalDisplay;
    }

    public void setAdditionalDisplay(final BiConsumer<IMultiController, List<Component>> additionalDisplay) {
        this.additionalDisplay = additionalDisplay;
    }
}
