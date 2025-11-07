package com.gregtechceu.gtceu.utils;

import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.core.ILevel;
import com.gregtechceu.gtceu.utils.collection.FastObjectArrayList;

import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.function.BooleanSupplier;

public class TaskHandler {

    private static final Reference2ReferenceOpenHashMap<ServerLevel, FastObjectArrayList<RunnableEntry>> serverTasks = new Reference2ReferenceOpenHashMap<>();

    // schedule tick event here
    public static void onTickUpdate(ServerLevel level) {
        var list = ILevel.getTasks(level);
        synchronized (list) {
            if (!list.isEmpty()) {
                serverTasks.computeIfAbsent(level, k -> new FastObjectArrayList<>()).addAll(list);
                list.clear();
            }
        }
        var tasks = serverTasks.get(level);
        if (tasks == null || tasks.isEmpty()) return;
        int tick = level.getServer().getTickCount();
        Object[] array = tasks.getArray();
        for (int i = 0, size = tasks.size(); i < size; i++) {
            var o = array[i];
            if (o == null) continue;
            var task = (RunnableEntry) o;
            if (task.delay > 0) {
                task.delay--;
            } else {
                if (task.stillSubscribed) {
                    if (task.task) {
                        task.runnable.run();
                        tasks.fastRemove(i);
                    } else if (tick >= task.lastTick) {
                        if (task.remove.getAsBoolean()) {
                            task.stillSubscribed = false;
                        } else {
                            task.runnable.run();
                            task.lastTick = tick + task.cycle;
                        }
                    }
                } else {
                    tasks.fastRemove(i);
                }
            }
        }
    }

    // clean up here
    public static void onWorldUnLoad(ServerLevel level) {
        var tasks = serverTasks.remove(level);
        if (tasks != null) {
            tasks.forEach(TickableSubscription::unsubscribe);
            tasks.clear();
        }
        var list = ILevel.getTasks(level);
        synchronized (list) {
            list.forEach(TickableSubscription::unsubscribe);
            list.clear();
        }
    }

    public static void enqueueServerTask(ServerLevel level, Runnable task, int delay) {
        var entry = new RunnableEntry(task, RunnableEntry.FALSE, true, delay);
        var list = ILevel.getTasks(level);
        synchronized (list) {
            list.add(entry);
        }
    }

    public static TickableSubscription enqueueServerTick(ServerLevel level, BooleanSupplier isRemove, Runnable runnable, int cycle, int delay) {
        var entry = new RunnableEntry(runnable, isRemove, false, delay);
        entry.cycle = cycle;
        var list = ILevel.getTasks(level);
        synchronized (list) {
            list.add(entry);
            return entry;
        }
    }

    public static TickableSubscription enqueueServerTick(ServerLevel level, Runnable runnable, int cycle, int delay) {
        return enqueueServerTick(level, RunnableEntry.FALSE, runnable, cycle, delay);
    }

    public static class RunnableEntry extends TickableSubscription {

        private static final BooleanSupplier FALSE = () -> false;

        private final BooleanSupplier remove;
        private final boolean task;
        private int delay;

        private RunnableEntry(Runnable runnable, BooleanSupplier remove, boolean task, int delay) {
            super(runnable);
            this.remove = remove;
            this.task = task;
            this.delay = delay;
        }
    }
}
