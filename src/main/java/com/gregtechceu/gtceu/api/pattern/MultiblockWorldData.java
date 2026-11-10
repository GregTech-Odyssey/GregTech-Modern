package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.utils.PosUtils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import com.suried.gtoutils.level.LevelStore;
import com.suried.gtoutils.schedule.AsyncLane;
import com.suried.gtoutils.schedule.Cadence;
import com.suried.gtoutils.schedule.Schedulers;
import com.suried.gtoutils.schedule.Task;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultiblockWorldData {

    private static final Comparator<IMultiController> COMPARATOR = Comparator.comparingInt(c -> -c.checkPriority());

    private static final ExecutorService SERVICE = Executors.newSingleThreadExecutor(AsyncLane.threadFactory("Multiblock World Data"));
    private static final AsyncLane STRUCTURE_LANE = AsyncLane.create("gtceu:structure", SERVICE);
    private static final int SEARCH_PERIOD = 40;
    private static final int FIRST_SEARCH_DELAY = 200;
    private static final LevelStore<MultiblockWorldData> DATA = LevelStore.createWithLevel(MultiblockWorldData::new, MultiblockWorldData::clear);

    public static MultiblockWorldData getOrCreate(ServerLevel serverLevel) {
        return DATA.get(serverLevel);
    }

    @Nullable
    public static MultiblockWorldData get(Level level) {
        return DATA.getIfPresent(level);
    }

    private final Level level;

    public static void execute(Runnable task) {
        SERVICE.execute(task);
    }

    private Task subscription;

    private boolean firstLoad = true;

    private long searchFrom;

    private MultiblockWorldData(Level level) {
        this.level = level;
    }

    /**
     * Chunk pos mapping.
     */
    private final Long2ObjectOpenHashMap<Set<MultiblockState>> chunkPosMapping = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet shareds = new LongOpenHashSet();
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

    public void addShared(long pos) {
        synchronized (shareds) {
            shareds.add(pos);
        }
    }

    public void removeShared(long pos) {
        synchronized (shareds) {
            shareds.remove(pos);
        }
    }

    public boolean hasShared(long pos) {
        synchronized (shareds) {
            return shareds.contains(pos);
        }
    }

    public void addMapping(MultiblockState state) {
        synchronized (chunkPosMapping) {
            state.forEachStructurePos(posLong -> chunkPosMapping.computeIfAbsent(PosUtils.getChunkLong(posLong), c -> new ReferenceOpenHashSet<>()).add(state));
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
        if (Task.isActive(subscription)) return;
        var scheduler = Schedulers.async(level, STRUCTURE_LANE);
        if (firstLoad) searchFrom = scheduler.round() + FIRST_SEARCH_DELAY;
        subscription = scheduler.repeat(Cadence.every(SEARCH_PERIOD), this::searchingTask);
    }

    /**
     * remove async controller
     * 
     * @param controller controller
     */
    public void removeAsyncLogic(IMultiController controller) {
        if (controllers.remove(controller) && controllers.isEmpty()) {
            subscription = Task.cancel(subscription);
        }
    }

    private void searchingTask() {
        if (firstLoad && Schedulers.async(level, STRUCTURE_LANE).round() < searchFrom) return;
        firstLoad = false;
        var arr = controllers.toArray(new IMultiController[0]);
        Arrays.parallelSort(arr, COMPARATOR);
        for (var controller : arr) {
            if (!GTCEu.canGetServerLevel()) break;
            controller.asyncCheckPattern(this);
        }
    }

    public void clear() {
        firstLoad = true;
        subscription = Task.cancel(subscription);
        controllers.clear();
        shareds.clear();
        chunkPosMapping.clear();
    }
}
