package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext;
import com.gregtechceu.gtceu.core.ILevel;
import com.gregtechceu.gtceu.core.IServerChunkCache;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongConsumer;
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

    private Long2ObjectOpenHashMap<LevelChunk> chunkCache;
    public final LongOpenHashSet blockEntityCache;
    @Nullable
    private volatile FormedCells formed;

    public MultiblockState(IMultiController controller, Level world, BlockPos controllerPos) {
        this.controller = controller;
        this.world = world;
        this.controllerPos = controllerPos;
        this.error = UNINIT_ERROR;
        this.matchContext = new PatternMatchContext();
        this.chunkCache = new Long2ObjectOpenHashMap<>();
        this.blockEntityCache = new LongOpenHashSet();
        this.data = world instanceof ServerLevel serverLevel ? MultiblockWorldData.getOrCreate(serverLevel) : null;
    }

    private MultiblockState(MultiblockState source) {
        this.world = source.world;
        this.controller = source.controller;
        this.controllerPos = source.controllerPos;
        this.error = UNINIT_ERROR;
        this.matchContext = new PatternMatchContext();
        this.chunkCache = source.chunkCache;
        this.blockEntityCache = new LongOpenHashSet();
        this.data = source.data;
    }

    public static MultiblockState probe(MultiblockState source) {
        return new MultiblockState(source);
    }

    public void clear() {
        this.formed = null;
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
        this.chunkCache = new Long2ObjectOpenHashMap<>();
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
            this.blockState = blockStateAt(pos);
        }
        return this.blockState;
    }

    public BlockState blockStateAt(BlockPos pos) {
        if (!(world instanceof ServerLevel serverLevel)) return world.getBlockState(pos);
        int chunkX = pos.getX() >> 4, chunkZ = pos.getZ() >> 4;
        long key = ChunkPos.asLong(chunkX, chunkZ);
        var chunk = chunkCache.get(key);
        if (chunk == null) {
            if (!(serverLevel.getChunkSource() instanceof IServerChunkCache cache)) return ILevel.OUTSIDE_WORLD_BLOCK;
            chunk = cache.gtceu$getCachedChunk(chunkX, chunkZ);
            if (chunk == null) return ILevel.OUTSIDE_WORLD_BLOCK;
            chunkCache.put(key, chunk);
        }
        return chunk.getBlockState(pos);
    }

    public boolean inStructure(long pos) {
        var cells = formed;
        return cells != null ? cells.contains(pos) : cache.contains(pos);
    }

    public void forEachStructurePos(LongConsumer consumer) {
        var cells = formed;
        if (cells != null) cells.forEach(consumer);
        else cache.forEach(consumer);
    }

    public void freeze() {
        var cells = FormedCells.of(cache);
        if (cells == null) return;
        formed = cells;
        cache.clear();
        cache.trim();
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
