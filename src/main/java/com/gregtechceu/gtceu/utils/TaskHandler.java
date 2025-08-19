package com.gregtechceu.gtceu.utils;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Map;

public class TaskHandler {

    private static final Map<ResourceLocation, List<RunnableEntry>> serverTasks = new Object2ObjectOpenHashMap<>();
    private static final Map<ResourceLocation, List<RunnableEntry>> waitToAddTasks = new Object2ObjectOpenHashMap<>();

    // schedule tick event here
    public static void onTickUpdate(ServerLevel level) {
        var key = level.dimension().location();
        synchronized (waitToAddTasks) {
            var list = waitToAddTasks.remove(key);
            if (list != null && !list.isEmpty()) {
                serverTasks.computeIfAbsent(key, k -> new ObjectArrayList<>()).addAll(list);
            }
        }
        execute(serverTasks.get(key));
    }

    // clean up here
    public static void onWorldUnLoad(ServerLevel level) {
        var key = level.dimension().location();
        var tasks = serverTasks.get(key);
        if (tasks != null) {
            tasks.forEach(TickableSubscription::unsubscribe);
            serverTasks.remove(key);
        }
        synchronized (waitToAddTasks) {
            tasks = waitToAddTasks.get(key);
            if (tasks != null) {
                tasks.forEach(TickableSubscription::unsubscribe);
                waitToAddTasks.remove(key);
            }
        }
    }

    private static void execute(List<RunnableEntry> tasks) {
        if (tasks == null || tasks.isEmpty()) return;
        var iter = tasks.iterator();
        while (iter.hasNext()) {
            var task = iter.next();
            if (task.delay <= 0) {
                if (task.isStillSubscribed()) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        GTCEu.LOGGER.error("error while schedule gregtech task", e);
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
            waitToAddTasks.computeIfAbsent(level.dimension().location(), key -> new ObjectArrayList<>()).add(entry);
        }
    }

    public static TickableSubscription enqueueServerTick(ServerLevel level, Runnable runnable, int delay) {
        var entry = new RunnableEntry(runnable, delay);
        synchronized (waitToAddTasks) {
            waitToAddTasks.computeIfAbsent(level.dimension().location(), key -> new ObjectArrayList<>()).add(entry);
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
