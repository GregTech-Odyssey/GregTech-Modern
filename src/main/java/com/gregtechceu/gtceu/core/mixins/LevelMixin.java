package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.pattern.MultiblockWorldData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import com.suried.gtoutils.level.LoadedChunks;
import com.suried.gtoutils.schedule.AsyncLane;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Level.class)
public abstract class LevelMixin implements LevelAccessor {

    @Unique
    private static final BlockState gtceu$AIR = Blocks.AIR.defaultBlockState();

    @Shadow
    @Final
    public boolean isClientSide;

    @Shadow
    @Final
    private Thread thread;

    @Shadow
    public abstract LevelChunk getChunk(int chunkX, int chunkZ);

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
        if (async && !this.isClientSide) {
            var chunk = LoadedChunks.chunk((Level) (Object) this, chunkX, chunkZ);
            return chunk != null ? chunk.getBlockEntities().get(pos) : null;
        }
        return this.getChunk(chunkX, chunkZ).getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
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
        var async = Thread.currentThread() != this.thread;
        if (async && !this.isClientSide) {
            var chunk = LoadedChunks.chunk((Level) (Object) this, chunkX, chunkZ);
            if (chunk != null) sections = chunk.getSections();
            else if (AsyncLane.isWorkerThread()) return LoadedChunks.UNLOADED;
        }
        if (sections == null) sections = this.getChunk(chunkX, chunkZ).getSections();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int chunkY = this.getSectionIndex(y);
        if (chunkY >= 0 && chunkY < sections.length) {
            LevelChunkSection section = sections[chunkY];
            return section != null && !section.hasOnlyAir() ? section.getBlockState(x & 15, y & 15, z & 15) : gtceu$AIR;
        } else {
            return LoadedChunks.UNLOADED;
        }
    }

    @Redirect(method = "getFluidState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isOutsideBuildHeight(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean skipTest(Level world, BlockPos pos) {
        return false;
    }

    @Inject(method = "markAndNotifyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlocksDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void gtceu$updateChunkMultiblocks(BlockPos pos, LevelChunk chunk, BlockState oldState, BlockState newState, int flags, int recursionLeft, CallbackInfo ci) {
        if (((Object) this) instanceof ServerLevel serverLevel) {
            var data = MultiblockWorldData.get(serverLevel);
            if (data != null) {
                var states = data.getControllersInChunk(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
                if (states != null) {
                    var pl = pos.asLong();
                    for (var structure : states) {
                        if (structure.inStructure(pl)) {
                            serverLevel.getServer().executeBlocking(() -> structure.onBlockStateChanged(pos, oldState, newState));
                        }
                    }
                }
            }
        }
    }
}
