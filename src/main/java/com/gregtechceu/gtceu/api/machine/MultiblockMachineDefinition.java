package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblockpro.MachineProtocol;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Structure;
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

public class MultiblockMachineDefinition extends MachineDefinition {

    protected int checkPriority;
    @Getter
    @Setter
    protected boolean generator;

    @Nullable
    protected StructureFactory structureFactory;
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
        return structureFactory != null;
    }

    @Nullable
    public Structure getStructure() {
        var factory = structureFactory;
        return factory == null ? null : factory.get();
    }

    @Nullable
    public Structure displayStructure() {
        var factory = structureFactory;
        return factory == null ? null : factory.peek();
    }

    @Nullable
    public StructureInfo getStructureInfo() {
        var info = structureInfo;
        if (info == null) {
            var structure = displayStructure();
            if (structure != null) {
                info = new StructureInfo(structure.getWidth(), structure.getHeight(), structure.getDepth(), structure.optionalModuleCount());
                structureInfo = info;
            }
        }
        return info;
    }

    public void setCheckPriority(final int checkPriority) {
        if (this.checkPriority == 0) {
            this.checkPriority = checkPriority;
        }
    }

    public void setStructure(final Function<MultiblockMachineDefinition, Structure> factory) {
        this.structureFactory = new StructureFactory(this, factory);
    }

    public void setRecoveryItems(@Nullable final MultiblockMachineBuilder.MufflerProductionGenerator recoveryItems) {
        this.recoveryItems = recoveryItems;
    }

    @Nullable
    public MultiblockMachineBuilder.MufflerProductionGenerator getRecoveryItems() {
        return this.recoveryItems;
    }

    public record StructureInfo(int width, int height, int depth, int optionalModules) {}

    private static final class StructureFactory {

        private final MultiblockMachineDefinition definition;
        private final Function<MultiblockMachineDefinition, Structure> factory;
        @Nullable
        private volatile SoftReference<Structure> cached;
        @Nullable
        private volatile WeakReference<Structure> shared;

        private StructureFactory(MultiblockMachineDefinition definition, Function<MultiblockMachineDefinition, Structure> factory) {
            this.definition = definition;
            this.factory = factory;
        }

        private Structure get() {
            var soft = cached;
            var structure = soft == null ? null : soft.get();
            if (structure != null) return structure;
            structure = peek();
            cached = new SoftReference<>(structure);
            return structure;
        }

        private Structure peek() {
            var soft = cached;
            var structure = soft == null ? null : soft.get();
            if (structure != null) return structure;
            var weak = shared;
            structure = weak == null ? null : weak.get();
            if (structure != null) return structure;
            structure = factory.apply(definition).bind(definition);
            shared = new WeakReference<>(structure);
            return structure;
        }
    }
}
