package com.gregtechceu.gtceu.utils;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;

import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.List;
import java.util.Map;

public class TaskHandler {

    private static final Map<ServerLevel, List<RunnableEntry>> serverTasks = new Reference2ReferenceOpenHashMap<>();
    private static final Map<ServerLevel, List<RunnableEntry>> waitToAddTasks = new Reference2ReferenceOpenHashMap<>();

    // schedule tick event here
    public static void onTickUpdate(ServerLevel level) {
        synchronized (waitToAddTasks) {
            var list = waitToAddTasks.remove(level);
            if (list != null && !list.isEmpty()) {
                serverTasks.computeIfAbsent(level, k -> new ObjectArrayList<>()).addAll(list);
            }
        }
        execute(serverTasks.get(level));
    }

    // clean up here
    public static void onWorldUnLoad(ServerLevel level) {
        var tasks = serverTasks.remove(level);
        if (tasks != null) {
            tasks.forEach(TickableSubscription::unsubscribe);
        }
        synchronized (waitToAddTasks) {
            tasks = waitToAddTasks.remove(level);
            if (tasks != null) {
                tasks.forEach(TickableSubscription::unsubscribe);
            }
        }
    }

    private static void execute(List<RunnableEntry> tasks) {
        if (tasks == null || tasks.isEmpty()) return;
        var iter = tasks.listIterator(0);
        while (iter.hasNext()) {
            var task = iter.next();
            if (task.delay <= 0) {
                if (task.isStillSubscribed()) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        GTCEu.LOGGER.error("error while run gregtech task", e);
                    }
                    if (task.task) iter.remove();
                } else {
                    iter.remove();
                }
            } else {
                task.delay--;
            }
        }
    }

    public static void enqueueServerTask(ServerLevel level, Runnable task, int delay) {
        var entry = new RunnableEntry(task, delay);
        entry.task = true;
        synchronized (waitToAddTasks) {
            waitToAddTasks.computeIfAbsent(level, key -> new ObjectArrayList<>()).add(entry);
        }
    }

    public static TickableSubscription enqueueServerTick(ServerLevel level, Runnable runnable, int delay) {
        var entry = new RunnableEntry(runnable, delay);
        synchronized (waitToAddTasks) {
            waitToAddTasks.computeIfAbsent(level, key -> new ObjectArrayList<>()).add(entry);
            return entry;
        }
    }

    private static class RunnableEntry extends TickableSubscription {

        private boolean task;
        private int delay;

        public RunnableEntry(Runnable runnable, int delay) {
            super(runnable);
            this.delay = delay;
        }
    }
}
