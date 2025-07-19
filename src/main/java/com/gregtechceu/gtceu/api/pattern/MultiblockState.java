package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

public class MultiblockState {

    public static final PatternError UNLOAD_ERROR = new PatternStringError("multiblocked.pattern.error.chunk");
    public static final PatternError UNINIT_ERROR = new PatternStringError("multiblocked.pattern.error.init");
    public BlockPos pos;
    public BlockState blockState;
    public BlockEntity tileEntity;
    public boolean tileEntityInitialized;
    public final PatternMatchContext matchContext;
    public Object2IntOpenHashMap<SimplePredicate> globalCount;
    public Object2IntOpenHashMap<SimplePredicate> layerCount;
    public TraceabilityPredicate predicate;
    public IO io;
    public PatternError error;
    public boolean neededFlip = false;
    public final Level world;
    public final BlockPos controllerPos;
    public IMultiController lastController;
    // persist
    public LongOpenHashSet cache;

    public Long2ObjectOpenHashMap<BlockState> blockStateCache;

    public MultiblockState(Level world, BlockPos controllerPos) {
        this.world = world;
        this.controllerPos = controllerPos;
        this.error = UNINIT_ERROR;
        this.matchContext = new PatternMatchContext();
    }

    public void clean() {
        this.matchContext.reset();
        this.globalCount = new Object2IntOpenHashMap<>();
        this.layerCount = new Object2IntOpenHashMap<>();
        cache = new LongOpenHashSet();
        blockStateCache = new Long2ObjectOpenHashMap<>();
    }

    public void cleanCache() {
        this.globalCount = null;
        this.layerCount = null;
        this.blockStateCache = null;
        this.blockState = null;
        this.tileEntity = null;
        this.tileEntityInitialized = false;
    }

    public boolean update(BlockPos posIn, TraceabilityPredicate predicate) {
        this.pos = posIn;
        this.blockState = null;
        this.tileEntity = null;
        this.tileEntityInitialized = false;
        this.predicate = predicate;
        this.error = null;
        if (!world.isLoaded(posIn)) {
            error = UNLOAD_ERROR;
            return false;
        }
        return true;
    }

    public IMultiController getController() {
        if (world.isLoaded(controllerPos)) {
            if (world.getBlockEntity(controllerPos) instanceof IMachineBlockEntity machineBlockEntity && machineBlockEntity.getMetaMachine() instanceof IMultiController controller) {
                return lastController = controller;
            }
        } else {
            error = UNLOAD_ERROR;
        }
        return null;
    }

    public boolean hasError() {
        return error != null;
    }

    public void setError(PatternError error) {
        this.error = error;
        if (error != null) {
            error.setWorldState(this);
        }
    }

    public BlockState getBlockState() {
        if (this.blockState == null) {
            this.blockState = blockStateCache.computeIfAbsent(pos.asLong(), k -> this.world.getBlockState(this.pos));
        }
        return this.blockState;
    }

    @Nullable
    public BlockEntity getTileEntity() {
        if (!getBlockState().hasBlockEntity()) {
            return null;
        }
        if (this.tileEntity == null && !this.tileEntityInitialized) {
            this.tileEntity = this.world.getBlockEntity(this.pos);
            this.tileEntityInitialized = true;
        }
        return this.tileEntity;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public BlockState getOffsetState(Direction face) {
        if (pos instanceof BlockPos.MutableBlockPos) {
            ((BlockPos.MutableBlockPos) pos).move(face);
            BlockState blockState = world.getBlockState(pos);
            ((BlockPos.MutableBlockPos) pos).move(face.getOpposite());
            return blockState;
        }
        return world.getBlockState(this.pos.relative(face));
    }

    public Level getWorld() {
        return world;
    }

    public void addPosCache(BlockPos pos) {
        cache.add(pos.asLong());
    }

    public boolean isPosInCache(BlockPos pos) {
        return cache.contains(pos.asLong());
    }

    public void onBlockStateChanged(BlockPos pos, BlockState state) {
        if (world instanceof ServerLevel serverLevel) {
            if (pos.equals(controllerPos)) {
                if (lastController != null) {
                    if (!state.is(lastController.self().getBlockState().getBlock())) {
                        lastController.onStructureInvalid();
                        var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
                        mwsd.removeMapping(this);
                    }
                }
            } else {
                IMultiController controller = getController();
                if (controller.isFormed()) {
                    if (state.getBlock() instanceof ActiveBlock) {
                        LongSet activeBlocks = getMatchContext().getOrDefault("vaBlocks", LongSets.emptySet());
                        if (activeBlocks.contains(pos.asLong())) {
                            return;
                        }
                    }
                    controller.requestCheck();
                }
            }
        }
    }

    public PatternMatchContext getMatchContext() {
        return this.matchContext;
    }

    public Object2IntOpenHashMap<SimplePredicate> getGlobalCount() {
        return this.globalCount;
    }

    public Object2IntOpenHashMap<SimplePredicate> getLayerCount() {
        return this.layerCount;
    }

    public boolean isNeededFlip() {
        return this.neededFlip;
    }

    public void setNeededFlip(final boolean neededFlip) {
        this.neededFlip = neededFlip;
    }
}
