package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;

import com.lowdragmc.lowdraglib.async.AsyncThreadData;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

import com.mojang.datafixers.util.Either;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(ServerChunkCache.class)
public abstract class ServerChunkProviderMixin {

    @Shadow
    @Final
    Thread mainThread;

    @Unique
    private final long[] gtceu$mbdLastChunkPos = new long[4];

    @Unique
    private final LevelChunk[] gtceu$mbdLastChunk = new LevelChunk[4];

    @Shadow
    @Nullable
    protected abstract ChunkHolder getVisibleChunkIfPresent(long p_217213_1_);

    @Unique
    private void gtceu$storeInCache(long pos, LevelChunk chunkAccess) {
        synchronized (this.gtceu$mbdLastChunkPos) {
            for (int i = 3; i > 0; --i) {
                this.gtceu$mbdLastChunkPos[i] = this.gtceu$mbdLastChunkPos[i - 1];
                this.gtceu$mbdLastChunk[i] = this.gtceu$mbdLastChunk[i - 1];
            }

            this.gtceu$mbdLastChunkPos[0] = pos;
            this.gtceu$mbdLastChunk[0] = chunkAccess;
        }
    }

    @Inject(method = "clearCache", at = @At(value = "TAIL"))
    private void injectClearCache(CallbackInfo ci) {
        synchronized (this.gtceu$mbdLastChunkPos) {
            Arrays.fill(this.gtceu$mbdLastChunkPos, ChunkPos.INVALID_CHUNK_POS);
            Arrays.fill(this.gtceu$mbdLastChunk, null);
        }
    }

    @Inject(method = "getChunkNow", at = @At(value = "HEAD"), cancellable = true)
    private void getTileEntity(int pChunkX, int pChunkZ, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() != this.mainThread &&
                (MultiblockWorldSavedData.isThreadService() || AsyncThreadData.isThreadService())) {
            long i = ChunkPos.asLong(pChunkX, pChunkZ);

            for (int j = 0; j < 4; ++j) {
                if (i == this.gtceu$mbdLastChunkPos[j]) {
                    cir.setReturnValue(this.gtceu$mbdLastChunk[j]);
                    return;
                }
            }

            ChunkHolder chunkholder = this.getVisibleChunkIfPresent(i);
            if (chunkholder != null) {
                Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> either = chunkholder
                        .getFutureIfPresent(ChunkStatus.FULL).getNow(null);
                if (either != null) {
                    ChunkAccess chunk = either.left().orElse(null);
                    if (chunk instanceof LevelChunk levelChunk) {
                        gtceu$storeInCache(i, levelChunk);
                        cir.setReturnValue(levelChunk);
                        return;
                    }
                }
            }
            cir.setReturnValue(null);
        }
    }
}
