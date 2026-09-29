package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblockpro.MachineProtocol;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructurePattern;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.registry.registrate.MultiblockMachineBuilder;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Collections;
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
    protected List<MachineProtocol> mountedOn = Collections.emptyList();

    protected MultiblockMachineDefinition(ResourceLocation id) {
        super(id);
    }

    public static MultiblockMachineDefinition createDefinition(ResourceLocation id) {
        return new MultiblockMachineDefinition(id);
    }

    @Nullable
    private volatile StructureInfo structureInfo;
    private volatile boolean priorityResolved;

    public int checkPriority() {
        if (checkPriority == 0 && !priorityResolved) {
            var info = getStructureInfo();
            if (info != null && checkPriority == 0) checkPriority = -(info.width() * info.height() * info.depth());
            priorityResolved = true;
        }
        return checkPriority;
    }

    public boolean hasStructure() {
        return patternFactory != null && patternFactory.length > 0;
    }

    @Nullable
    public BlockPattern displayPattern() {
        if (!hasStructure()) return null;
        return patternFactory[0] instanceof PatternFactory factory ? factory.peek() : patternFactory[0].get();
    }

    @Nullable
    public StructureInfo getStructureInfo() {
        var info = structureInfo;
        if (info == null && displayPattern() instanceof StructurePattern pattern) {
            info = new StructureInfo(pattern.getWidth(), pattern.getHeight(), pattern.getDepth(), pattern.getStructure().optionalModuleCount());
            structureInfo = info;
        }
        return info;
    }

    public void setCheckPriority(final int checkPriority) {
        if (this.checkPriority == 0) {
            this.checkPriority = checkPriority;
        }
    }

    public void setPatternFactory(final List<Function<MultiblockMachineDefinition, BlockPattern>> patternFactory) {
        this.patternFactory = patternFactory.stream().map(p -> new PatternFactory(this, p)).toArray(Supplier[]::new);
    }

    public void setRecoveryItems(@Nullable final MultiblockMachineBuilder.MufflerProductionGenerator recoveryItems) {
        this.recoveryItems = recoveryItems;
    }

    @Nullable
    public MultiblockMachineBuilder.MufflerProductionGenerator getRecoveryItems() {
        return this.recoveryItems;
    }

    public record StructureInfo(int width, int height, int depth, int optionalModules) {}

    public static final class PatternFactory implements Supplier<BlockPattern> {

        private final MultiblockMachineDefinition definition;
        private final Function<MultiblockMachineDefinition, BlockPattern> factory;
        @Nullable
        private volatile SoftReference<BlockPattern> cached;
        @Nullable
        private volatile WeakReference<BlockPattern> shared;

        private PatternFactory(MultiblockMachineDefinition definition, Function<MultiblockMachineDefinition, BlockPattern> factory) {
            this.definition = definition;
            this.factory = factory;
        }

        @Override
        public BlockPattern get() {
            var soft = cached;
            var pattern = soft == null ? null : soft.get();
            if (pattern != null) return pattern;
            pattern = peek();
            cached = new SoftReference<>(pattern);
            return pattern;
        }

        public BlockPattern peek() {
            var soft = cached;
            var pattern = soft == null ? null : soft.get();
            if (pattern != null) return pattern;
            var weak = shared;
            pattern = weak == null ? null : weak.get();
            if (pattern != null) return pattern;
            pattern = factory.apply(definition);
            shared = new WeakReference<>(pattern);
            return pattern;
        }

        public void invalidate() {
            cached = null;
            shared = null;
        }
    }
}
