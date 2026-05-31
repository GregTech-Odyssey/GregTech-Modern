package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext;
import com.gregtechceu.gtceu.core.ILevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MultiblockState {

    public static final PatternStringError BANNED_ERROR = new PatternStringError("multiblocked.pattern.error.banned");
    public static final PatternStringError SHARE_ERROR = new PatternStringError("multiblocked.pattern.error.share");
    public static final PatternStringError UNLOAD_ERROR = new PatternStringError("multiblocked.pattern.error.chunk");
    public static final PatternStringError UNINIT_ERROR = new PatternStringError("multiblocked.pattern.error.init");

    @Getter
    public BlockPos pos;
    public BlockState blockState;
    public BlockEntity tileEntity;
    public boolean tileEntityInitialized;
    @Getter
    public Reference2IntOpenHashMap<SimplePredicate> globalCount = new Reference2IntOpenHashMap<>();
    @Getter
    public Reference2IntOpenHashMap<SimplePredicate> layerCount = new Reference2IntOpenHashMap<>();
    public TraceabilityPredicate predicate;
    public PatternError error;
    @Getter
    @Setter
    public boolean neededFlip = false;

    @Getter
    public final Level world;
    @Nullable
    public final MultiblockWorldData data;
    public final BlockPos controllerPos;
    public final IMultiController controller;
    // persist
    @Getter
    public final PatternMatchContext matchContext;
    public final LongOpenHashSet cache = new LongOpenHashSet();
    public final LongOpenHashSet sharedCache = new LongOpenHashSet();

    public final List<PatternError> errorRecord = new ArrayList<>();

    public Long2ObjectOpenHashMap<BlockState> blockStateCache;
    public final LongOpenHashSet blockEntityCache;

    public MultiblockState(IMultiController controller, Level world, BlockPos controllerPos) {
        this.controller = controller;
        this.world = world;
        this.controllerPos = controllerPos;
        this.error = UNINIT_ERROR;
        this.matchContext = new PatternMatchContext();
        this.blockStateCache = new Long2ObjectOpenHashMap<>();
        this.blockEntityCache = new LongOpenHashSet();
        this.data = world instanceof ServerLevel serverLevel ? MultiblockWorldData.getOrCreate(serverLevel) : null;
    }

    @SuppressWarnings("all")
    private MultiblockState(MultiblockState state) {
        this.world = state.world;
        this.controller = state.controller;
        this.controllerPos = state.controllerPos;
        this.error = UNINIT_ERROR;
        this.matchContext = new PatternMatchContext(state.matchContext);
        this.blockStateCache = state.blockStateCache;
        this.blockEntityCache = state.blockEntityCache;
        this.data = state.data;
    }

    public static MultiblockState copy(MultiblockState state) {
        return new MultiblockState(state);
    }

    public void merge(MultiblockState state) {
        this.matchContext.merge(state.matchContext);
        this.cache.addAll(state.cache);
        this.sharedCache.addAll(state.sharedCache);
        this.blockEntityCache.addAll(state.blockEntityCache);
    }

    public void clear() {
        this.removeShared();
        this.matchContext.reset();
        this.globalCount.clear();
        this.layerCount.clear();
        this.cache.clear();
        this.sharedCache.clear();
        this.blockEntityCache.clear();
    }

    public void clearCache() {
        this.globalCount = new Reference2IntOpenHashMap<>();
        this.layerCount = new Reference2IntOpenHashMap<>();
        this.blockStateCache = new Long2ObjectOpenHashMap<>();
        this.predicate = null;
        this.blockState = null;
        this.tileEntity = null;
        this.tileEntityInitialized = false;
    }

    public void removeShared() {
        if (data != null) this.sharedCache.forEach(data::removeShared);
    }

    public void addShared() {
        if (data != null) this.sharedCache.forEach(data::addShared);
    }

    public void update(BlockPos posIn, TraceabilityPredicate predicate) {
        this.pos = posIn;
        this.blockState = null;
        this.tileEntity = null;
        this.tileEntityInitialized = false;
        this.predicate = predicate;
        this.error = null;
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
            this.blockState = blockStateCache.computeIfAbsent(pos.asLong(), k -> ILevel.asyncGetBlockState(world, pos));
        }
        return this.blockState;
    }

    @Nullable
    public BlockEntity getTileEntity() {
        if (this.tileEntityInitialized) return tileEntity;
        if (getBlockState().hasBlockEntity()) {
            this.tileEntity = ILevel.asyncGetBlockEntity(world, pos);
        } else {
            this.tileEntity = null;
        }
        this.tileEntityInitialized = true;
        return this.tileEntity;
    }

    public void onBlockStateChanged(BlockPos pos, BlockState state) {
        if (world instanceof ServerLevel serverLevel) {
            if (pos.equals(controllerPos)) {
                if (!state.is(controller.self().getBlockState().getBlock())) {
                    controller.onStructureInvalid();
                    var mwsd = MultiblockWorldData.getOrCreate(serverLevel);
                    mwsd.removeMapping(this);
                }
            } else {
                if (controller.isFormed()) {
                    if (state.getBlock() instanceof ActiveBlock) {
                        if (matchContext.getOrDefault(Predicates.DataKey.ACTIVE_BLOCKS, LongSets.EMPTY_SET).contains(pos.asLong())) {
                            return;
                        }
                    }
                    controller.requestCheck();
                }
            }
        }
    }
}
