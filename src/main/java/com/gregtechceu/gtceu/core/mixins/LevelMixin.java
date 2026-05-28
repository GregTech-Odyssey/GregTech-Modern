package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.pattern.MultiblockWorldData;
import com.gregtechceu.gtceu.core.ILevel;
import com.gregtechceu.gtceu.core.IServerChunkCache;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import com.gto.datasynclib.datasream.DataComponentMap;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Level.class)
public abstract class LevelMixin implements LevelAccessor, ILevel {

    @Shadow
    @Final
    public boolean isClientSide;

    @Shadow
    @Final
    private Thread thread;

    @Shadow
    public abstract LevelChunk getChunk(int chunkX, int chunkZ);

    @Shadow
    @Final
    private boolean isDebug;
    @Unique
    private volatile DataComponentMap gtceu$capabilitie;

    @Unique
    private volatile TaskHandler gtceu$taskHandler;

    @Unique
    private volatile TaskHandler gtceu$taskAsyncHandler;

    @Unique
    private MultiblockWorldData gtceu$multiblockWorldData;

    @Override
    public DataComponentMap gtceu$getCapabilities() {
        var cap = gtceu$capabilitie;
        if (cap == null) {
            synchronized (this) {
                if (gtceu$capabilitie == null) {
                    gtceu$capabilitie = new DataComponentMap();
                }
                cap = gtceu$capabilitie;
            }
        }
        return cap;
    }

    @Override
    public MultiblockWorldData gtceu$getMultiblockWorldSavedData() {
        return gtceu$multiblockWorldData;
    }

    @Override
    public void gtceu$setMultiblockWorldSavedData(MultiblockWorldData data) {
        gtceu$multiblockWorldData = data;
    }

    @Override
    public @NotNull TaskHandler gtceu$getTaskHandler() {
        var handler = gtceu$taskHandler;
        if (handler == null) {
            synchronized (this) {
                if (gtceu$taskHandler == null) {
                    gtceu$taskHandler = TaskHandler.create();
                }
                handler = gtceu$taskHandler;
            }
        }
        return handler;
    }

    @Override
    public @NotNull TaskHandler gtceu$getAsyncTaskHandler() {
        var handler = gtceu$taskAsyncHandler;
        if (handler == null) {
            synchronized (this) {
                if (gtceu$taskAsyncHandler == null) {
                    gtceu$taskAsyncHandler = TaskHandler.createAsync(GTUtil.ASYNC_EXECUTOR, 50);
                }
                handler = gtceu$taskAsyncHandler;
            }
        }
        return handler;
    }

    @Override
    public void gtceu$clear() {
        gtceu$taskAsyncHandler = null;
        gtceu$taskHandler = null;
        gtceu$multiblockWorldData = null;
        gtceu$capabilitie = null;
    }

    /**
     * @author .
     * @reason .
     */
    @Nullable
    @Overwrite
    public BlockEntity getBlockEntity(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        var async = Thread.currentThread() != this.thread;
        if (async && getChunkSource() instanceof IServerChunkCache cache) {
            var chunk = cache.gtceu$getCachedChunk(chunkX, chunkZ);
            if (chunk != null) return chunk.getBlockEntities().get(pos);
        }
        return async && !this.isClientSide ? null : this.getChunk(chunkX, chunkZ).getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite
    public @NotNull BlockState getBlockState(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        LevelChunkSection[] sections = null;
        var currentThread = Thread.currentThread();
        var async = currentThread != this.thread;
        if (async && getChunkSource() instanceof IServerChunkCache cache) {
            var chunk = cache.gtceu$getCachedChunk(chunkX, chunkZ);
            if (chunk != null) {
                sections = chunk.getSections();
            }
        }
        if (sections == null) {
            if (async && currentThread instanceof GTUtil.AsyncExecutorThread) return OUTSIDE_WORLD_BLOCK;
            sections = this.getChunk(chunkX, chunkZ).getSections();
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int chunkY = this.getSectionIndex(y);
        if (chunkY >= 0 && chunkY < sections.length) {
            LevelChunkSection section = sections[chunkY];
            return section != null && !section.hasOnlyAir() ? section.getBlockState(x & 15, y & 15, z & 15) : INSIDE_WORLD_DEFAULT_BLOCK;
        } else {
            return OUTSIDE_WORLD_BLOCK;
        }
    }

    @Redirect(method = "getFluidState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isOutsideBuildHeight(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean skipTest(Level world, BlockPos pos) {
        return false;
    }

    @Inject(method = "markAndNotifyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;blockUpdated(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V"))
    private void gtceu$updateChunkMultiblocks(BlockPos pos, LevelChunk chunk, BlockState oldState, BlockState newState, int flags, int recursionLeft, CallbackInfo ci) {
        if (((Object) this) instanceof ServerLevel serverLevel) {
            if (gtceu$multiblockWorldData != null) {
                var states = gtceu$multiblockWorldData.getControllersInChunk(chunk.getPos().toLong());
                if (states != null) {
                    var pl = pos.asLong();
                    for (var structure : states) {
                        if (structure.cache.contains(pl)) {
                            serverLevel.getServer().executeBlocking(() -> structure.onBlockStateChanged(pos, newState));
                        }
                    }
                }
            }
        }
    }
}
