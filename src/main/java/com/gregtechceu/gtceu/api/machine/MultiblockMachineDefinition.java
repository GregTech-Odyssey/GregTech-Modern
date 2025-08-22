package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.utils.memoization.GTMemoizer;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class MultiblockMachineDefinition extends MachineDefinition {

    protected int checkPriority;
    protected boolean generator;

    protected Supplier<BlockPattern> patternFactory;
    protected Supplier<BlockPattern>[] subPatternFactory;
    protected Supplier<List<MultiblockShapeInfo>> shapes;
    /**
     * Set this to false only if your multiblock is set up such that it could have a wall-shared controller.
     */
    protected boolean allowFlip;
    protected boolean renderXEIPreview;
    @Nullable
    protected Supplier<ItemStack> recoveryItems;
    protected TriFunction<IMultiController, IMultiPart, Direction, BlockState> partAppearance;
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
        var structurePattern = patternFactory.get();
        int[][] aisleRepetitions = structurePattern.aisleRepetitions;
        return repetitionDFS(structurePattern, new ObjectArrayList<>(), aisleRepetitions, new IntArrayList());
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

    public int checkPriority() {
        return checkPriority;
    }

    public void setCheckPriority(final int checkPriority) {
        if (this.checkPriority == 0) {
            this.checkPriority = checkPriority;
        }
    }

    public boolean isGenerator() {
        return this.generator;
    }

    public void setGenerator(final boolean generator) {
        this.generator = generator;
    }

    public void setPatternFactory(final Function<MultiblockMachineDefinition, BlockPattern> patternFactory) {
        this.patternFactory = GTMemoizer.memoize(() -> patternFactory.apply(this));
    }

    public Supplier<BlockPattern> getPatternFactory() {
        return this.patternFactory;
    }

    public void setSubPatternFactory(final List<Function<MultiblockMachineDefinition, BlockPattern>> subPatternFactory) {
        this.subPatternFactory = subPatternFactory.stream().map(p -> GTMemoizer.memoize(() -> p.apply(this))).toArray(Supplier[]::new);
    }

    public Supplier<BlockPattern>[] getSubPatternFactory() {
        return this.subPatternFactory;
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

    public void setRecoveryItems(@Nullable final Supplier<ItemStack> recoveryItems) {
        this.recoveryItems = GTMemoizer.memoize(recoveryItems);
    }

    @Nullable
    public Supplier<ItemStack> getRecoveryItems() {
        return this.recoveryItems;
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
