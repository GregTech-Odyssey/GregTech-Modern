package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.ITickSubscription;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.core.ILevel;
import com.gregtechceu.gtceu.utils.PosUtils;
import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.*;

public class MultiblockWorldData {

    private static final Comparator<IMultiController> COMPARATOR = Comparator.comparingInt(c -> -c.checkPriority());

    public static MultiblockWorldData getOrCreate(ServerLevel serverLevel) {
        var data = ((ILevel) serverLevel).gtceu$getMultiblockWorldSavedData();
        if (data == null) {
            data = new MultiblockWorldData();
            ((ILevel) serverLevel).gtceu$setMultiblockWorldSavedData(data);
        }
        return data;
    }

    @Nullable
    public static MultiblockWorldData get(ServerLevel serverLevel) {
        return ((ILevel) serverLevel).gtceu$getMultiblockWorldSavedData();
    }

    public static final TaskHandler TASK_HANDLER = TaskHandler.createAsync(Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r);
        thread.setName("Multiblock World Data");
        thread.setPriority(1);
        thread.setDaemon(true);
        return thread;
    }), 2000);

    private TickableSubscription subscription;

    /**
     * Chunk pos mapping.
     */
    private final Long2ObjectOpenHashMap<Set<MultiblockState>> chunkPosMapping = new Long2ObjectOpenHashMap<>();
    private final Set<IMultiController> controllers = ConcurrentHashMap.newKeySet();

    public MultiblockState[] getControllersInChunk(long chunkPos) {
        synchronized (chunkPosMapping) {
            var states = chunkPosMapping.get(chunkPos);
            if (states != null) {
                return states.toArray(MultiblockState[]::new);
            }
            return null;
        }
    }

    public void addMapping(MultiblockState state) {
        synchronized (chunkPosMapping) {
            state.cache.forEach(posLong -> chunkPosMapping.computeIfAbsent(PosUtils.getChunkLong(posLong), c -> new ReferenceOpenHashSet<>()).add(state));
        }
    }

    public void removeMapping(MultiblockState state) {
        synchronized (chunkPosMapping) {
            for (Set<MultiblockState> set : chunkPosMapping.values()) {
                set.remove(state);
            }
        }
    }

    /**
     * add a async logic runnable
     * 
     * @param controller controller
     */
    public void addAsyncLogic(IMultiController controller) {
        controllers.add(controller);
        subscription = TASK_HANDLER.enqueueTick(subscription, this::searchingTask, 0, 0);
    }

    /**
     * remove async controller
     * 
     * @param controller controller
     */
    public void removeAsyncLogic(IMultiController controller) {
        if (controllers.remove(controller) && controllers.isEmpty()) {
            subscription = ITickSubscription.unsubscribe(subscription);
        }
    }

    private void searchingTask() {
        if (GTCEu.canGetServerLevel()) {
            var arr = controllers.toArray(new IMultiController[0]);
            Arrays.parallelSort(arr, COMPARATOR);
            for (var controller : arr) {
                controller.asyncCheckPattern(this);
            }
        }
    }

    public void clear() {
        subscription = ITickSubscription.unsubscribe(subscription);
        controllers.clear();
        chunkPosMapping.clear();
    }
}
