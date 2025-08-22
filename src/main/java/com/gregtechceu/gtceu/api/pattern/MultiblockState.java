package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MultiblockState {

    public static final PatternStringError BANNED_ERROR = new PatternStringError("multiblocked.pattern.error.banned");
    public static final PatternStringError SHARE_ERROR = new PatternStringError("multiblocked.pattern.error.share");
    public static final PatternStringError UNLOAD_ERROR = new PatternStringError("multiblocked.pattern.error.chunk");
    public static final PatternStringError UNINIT_ERROR = new PatternStringError("multiblocked.pattern.error.init");

    public BlockPos pos;
    public BlockState blockState;
    public BlockEntity tileEntity;
    public boolean tileEntityInitialized;
    public final Object2IntOpenHashMap<SimplePredicate> globalCount = new Object2IntOpenHashMap<>();
    public final Object2IntOpenHashMap<SimplePredicate> layerCount = new Object2IntOpenHashMap<>();
    public TraceabilityPredicate predicate;
    public PatternError error;
    public boolean neededFlip = false;

    public final Level world;
    public final BlockPos controllerPos;
    public final IMultiController controller;
    // persist
    public final PatternMatchContext matchContext;
    public final LongOpenHashSet cache = new LongOpenHashSet();

    public final List<PatternError> errorRecord = new ObjectArrayList<>();

    public final Long2ObjectOpenHashMap<BlockState> blockStateCache;

    public MultiblockState(IMultiController controller, Level world, BlockPos controllerPos) {
        this.controller = controller;
        this.world = world;
        this.controllerPos = controllerPos;
        this.error = UNINIT_ERROR;
        this.matchContext = new PatternMatchContext();
        this.blockStateCache = new Long2ObjectOpenHashMap<>();
    }

    @SuppressWarnings("all")
    private MultiblockState(MultiblockState state) {
        this.world = state.world;
        this.controller = state.controller;
        this.controllerPos = state.controllerPos;
        this.error = UNINIT_ERROR;
        this.matchContext = new PatternMatchContext();
        this.matchContext.mergeData(state.matchContext);
        this.blockStateCache = state.blockStateCache;
    }

    public static MultiblockState copy(MultiblockState state) {
        return new MultiblockState(state);
    }

    public void merge(MultiblockState state) {
        this.matchContext.merge(state.matchContext);
        this.cache.addAll(state.cache);
    }

    public void clean() {
        this.matchContext.reset();
        this.globalCount.clear();
        this.layerCount.clear();
        cache.clear();
    }

    public void cleanCache() {
        this.globalCount.clear();
        this.layerCount.clear();
        this.blockStateCache.clear();
        this.predicate = null;
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
        if (this.tileEntityInitialized) return tileEntity;
        if (getBlockState().hasBlockEntity()) {
            this.tileEntity = this.world.getBlockEntity(this.pos);
        } else {
            this.tileEntity = null;
        }
        this.tileEntityInitialized = true;
        return this.tileEntity;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public Level getWorld() {
        return world;
    }

    public void addPosCache(long pos) {
        cache.add(pos);
    }

    public boolean isPosInCache(BlockPos pos) {
        return cache.contains(pos.asLong());
    }

    public void onBlockStateChanged(BlockPos pos, BlockState state) {
        if (world instanceof ServerLevel serverLevel) {
            if (pos.equals(controllerPos)) {
                if (controller != null) {
                    if (!state.is(controller.self().getBlockState().getBlock())) {
                        controller.onStructureInvalid();
                        var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
                        mwsd.removeMapping(this);
                    }
                }
            } else {
                if (controller.isFormed()) {
                    if (state.getBlock() instanceof ActiveBlock) {
                        if (getMatchContext().vaBlocks.contains(pos.asLong())) {
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
