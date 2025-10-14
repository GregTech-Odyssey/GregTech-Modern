package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.core.ILevel;
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.async.AsyncThreadData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Level.class)
public abstract class LevelMixin implements LevelAccessor, ILevel {

    @Unique
    private static final BlockState OUTSIDE_WORLD_BLOCK = Blocks.VOID_AIR.defaultBlockState();
    @Unique
    private static final BlockState INSIDE_WORLD_DEFAULT_BLOCK = Blocks.AIR.defaultBlockState();

    @Shadow
    @Final
    public boolean isClientSide;

    @Shadow
    @Final
    private Thread thread;

    @Shadow
    public abstract LevelChunk getChunk(int chunkX, int chunkZ);

    @Unique
    private List<TaskHandler.RunnableEntry> gtceu$tasks;

    @Override
    public @NotNull List<TaskHandler.RunnableEntry> gtceu$getTasks() {
        if (gtceu$tasks == null) gtceu$tasks = new ObjectArrayList<>();
        return gtceu$tasks;
    }

    @Unique
    private @Nullable ChunkAccess gtceu$maybeGetChunkAsync(int chunkX, int chunkZ) {
        if (this.isClientSide) return null;
        if (Thread.currentThread() == this.thread) return null;
        if (!MultiblockWorldSavedData.isThreadService() && !AsyncThreadData.isThreadService()) return null;
        if (!this.getChunkSource().hasChunk(chunkX, chunkZ)) return null;
        return this.getChunkSource().getChunkNow(chunkX, chunkZ);
    }

    /**
     * @author .
     * @reason .
     */
    @javax.annotation.Nullable
    @Overwrite
    public BlockEntity getBlockEntity(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        ChunkAccess chunk = gtceu$maybeGetChunkAsync(chunkX, chunkZ);
        if (chunk instanceof LevelChunk levelChunk) {
            return levelChunk.getBlockEntities().get(pos);
        }
        return !this.isClientSide && Thread.currentThread() != this.thread ? null : this.getChunk(chunkX, chunkZ).getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite
    public @NotNull BlockState getBlockState(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        ChunkAccess chunk = gtceu$maybeGetChunkAsync(chunkX, chunkZ);
        if (chunk == null) {
            chunk = this.getChunk(chunkX, chunkZ);
        }
        LevelChunkSection[] sections = chunk.getSections();
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
            var cache = serverLevel.getDataStorage().cache.get(MultiblockWorldSavedData.DATA_NAME);
            if (cache != null) {
                var states = ((MultiblockWorldSavedData) cache).getControllersInChunk(chunk.getPos());
                if (states != null) {
                    for (var structure : states) {
                        if (structure.isPosInCache(pos)) {
                            serverLevel.getServer().executeBlocking(() -> structure.onBlockStateChanged(pos, newState));
                        }
                    }
                }
            }
        }
    }
}
