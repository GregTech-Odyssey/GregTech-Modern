package com.gregtechceu.gtceu.utils;

import com.gregtechceu.gtceu.api.machine.TickableSubscription;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.List;
import java.util.Map;

public class TaskHandler {

    private static final Map<ServerLevel, List<RunnableEntry>> serverTasks = new Reference2ReferenceOpenHashMap<>();
    private static final Map<ServerLevel, List<RunnableEntry>> waitToAddTasks = new Reference2ReferenceOpenHashMap<>();

    // schedule tick event here
    public static void onTickUpdate(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            synchronized (waitToAddTasks) {
                var list = waitToAddTasks.get(level);
                if (list != null && !list.isEmpty()) {
                    serverTasks.computeIfAbsent(level, k -> new ObjectArrayList<>()).addAll(list);
                    list.clear();
                }
            }
            var tasks = serverTasks.get(level);
            if (tasks == null || tasks.isEmpty()) return;
            var iter = tasks.listIterator(0);
            while (iter.hasNext()) {
                var task = iter.next();
                if (task.delay > 0) {
                    task.delay--;
                } else {
                    if (task.isStillSubscribed()) {
                        task.run();
                        if (task.task) {
                            task.unsubscribeCallback.run();
                            iter.remove();
                        }
                    } else {
                        iter.remove();
                    }
                }
            }
        }
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

    public static void enqueueServerTask(ServerLevel level, Runnable task, int delay) {
        enqueueServerTask(level, task, GTUtil.NOOP, delay);
    }

    public static void enqueueServerTask(ServerLevel level, Runnable task, Runnable unsubscribeCallback, int delay) {
        var entry = new RunnableEntry(task, unsubscribeCallback, true, delay);
        synchronized (waitToAddTasks) {
            waitToAddTasks.computeIfAbsent(level, key -> new ObjectArrayList<>()).add(entry);
        }
    }

    public static TickableSubscription enqueueServerTick(ServerLevel level, Runnable runnable, Runnable unsubscribeCallback, int delay) {
        var entry = new RunnableEntry(runnable, unsubscribeCallback, false, delay);
        synchronized (waitToAddTasks) {
            waitToAddTasks.computeIfAbsent(level, key -> new ObjectArrayList<>()).add(entry);
            return entry;
        }
    }

    private static class RunnableEntry extends TickableSubscription {

        private final Runnable unsubscribeCallback;
        private final boolean task;
        private int delay;

        public RunnableEntry(Runnable runnable, Runnable unsubscribeCallback, boolean task, int delay) {
            super(runnable);
            this.unsubscribeCallback = unsubscribeCallback;
            this.task = task;
            this.delay = delay;
        }

        @Override
        public void unsubscribe() {
            stillSubscribed = false;
            unsubscribeCallback.run();
        }
    }
}
