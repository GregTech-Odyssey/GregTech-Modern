package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.utils.PosUtils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.*;

public class MultiblockWorldSavedData extends SavedData {

    private static final Comparator<IMultiController> COMPARATOR = Comparator.comparingInt(c -> -c.checkPriority());

    public static final String DATA_NAME = "gtceu_multiblock";

    public static MultiblockWorldSavedData getOrCreate(ServerLevel serverLevel) {
        return serverLevel.getDataStorage()
                .computeIfAbsent(MultiblockWorldSavedData::new, MultiblockWorldSavedData::new, DATA_NAME);
    }

    /**
     * Store all formed multiblocks' structure info
     */
    public final Long2ReferenceOpenHashMap<MultiblockState> mapping;
    /**
     * Chunk pos mapping.
     */
    public final Long2ObjectOpenHashMap<Set<MultiblockState>> chunkPosMapping;

    private MultiblockWorldSavedData() {
        this.mapping = new Long2ReferenceOpenHashMap<>();
        this.chunkPosMapping = new Long2ObjectOpenHashMap<>();
    }

    private MultiblockWorldSavedData(CompoundTag tag) {
        this();
    }

    public Set<MultiblockState> getControllersInChunk(ChunkPos chunkPos) {
        return chunkPosMapping.getOrDefault(chunkPos.toLong(), Collections.emptySet());
    }

    public void addMapping(MultiblockState state) {
        this.mapping.put(state.controllerPos.asLong(), state);
        for (var blockPos : state.cache) {
            chunkPosMapping.computeIfAbsent(PosUtils.getChunkLong(blockPos), c -> new ReferenceOpenHashSet<>()).add(state);
        }
    }

    public void removeMapping(MultiblockState state) {
        this.mapping.remove(state.controllerPos.asLong());
        for (Set<MultiblockState> set : chunkPosMapping.values()) {
            set.remove(state);
        }
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag compound) {
        return compound;
    }

    // ********************************* thread for searching ********************************* //
    private final CopyOnWriteArrayList<IMultiController> controllers = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService executorService;
    private final static ThreadFactory THREAD_FACTORY = new ThreadFactoryBuilder()
            .setNameFormat("GTCEu Multiblock Async Thread-%d")
            .setDaemon(true)
            .build();
    private static final ThreadLocal<Boolean> IN_SERVICE = ThreadLocal.withInitial(() -> false);
    public int periodID = 0;
    private int waiting;

    public void createExecutorService() {
        if (executorService != null && !executorService.isShutdown()) return;
        executorService = Executors.newSingleThreadScheduledExecutor(THREAD_FACTORY);
        executorService.scheduleAtFixedRate(this::searchingTask, 0, 500, TimeUnit.MILLISECONDS);
        waiting = 10;
    }

    /**
     * add a async logic runnable
     * 
     * @param controller controller
     */
    public void addAsyncLogic(IMultiController controller) {
        controllers.addIfAbsent(controller);
        createExecutorService();
    }

    /**
     * remove async controller
     * 
     * @param controller controller
     */
    public void removeAsyncLogic(IMultiController controller) {
        if (controllers.remove(controller) && controllers.isEmpty()) {
            releaseExecutorService();
        }
    }

    private void searchingTask() {
        try {
            if (!GTCEu.canGetServerLevel()) return;
            boolean delay = waiting > 0;
            if (delay) {
                waiting--;
            }
            IN_SERVICE.set(true);
            var arr = controllers.toArray(new IMultiController[0]);
            Arrays.parallelSort(arr, COMPARATOR);
            for (var controller : arr) {
                if (delay && controller.checkPriority() < -1000) continue;
                try {
                    controller.asyncCheckPattern(this);
                } catch (Throwable e) {
                    GTCEu.LOGGER.error("Error while assembling multiblock {}: {}", controller, e.getMessage());
                }
            }
        } catch (Throwable e) {
            GTCEu.LOGGER.error("Error while assembling multiblocks: {}", e.getMessage());
        } finally {
            IN_SERVICE.set(false);
        }
        if (periodID > 100) {
            periodID = 0;
        } else {
            periodID++;
        }
    }

    public static boolean isThreadService() {
        return IN_SERVICE.get() && GTCEu.canGetServerLevel();
    }

    public void releaseExecutorService() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
        executorService = null;
    }
}
