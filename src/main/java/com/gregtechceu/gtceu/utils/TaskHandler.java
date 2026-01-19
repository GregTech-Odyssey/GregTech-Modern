package com.gregtechceu.gtceu.utils;

import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.core.ILevel;
import com.gregtechceu.gtceu.utils.collection.FastObjectArrayList;

import net.minecraft.world.level.Level;

import java.util.function.BooleanSupplier;

public class TaskHandler {

    // schedule tick event here
    public static void onTickUpdate(Level level, int tickCount) {
        var list = ILevel.getTasks(level);
        FastObjectArrayList<TaskRunnableEntry> tasks = ILevel.getCapability(level, TaskHandler.class);
        synchronized (list) {
            if (!list.isEmpty()) {
                if (tasks == null) {
                    tasks = new FastObjectArrayList<>();
                    ILevel.setCapability(level, TaskHandler.class, tasks);
                }
                tasks.addAll(list);
                list.clear();
            }
        }
        if (tasks == null || tasks.isEmpty()) return;
        Object[] array = tasks.getArray();
        for (int i = 0, size = tasks.size(); i < size; i++) {
            var o = array[i];
            if (o == null) continue;
            var task = (TaskRunnableEntry) o;
            if (task.delay > 0) {
                task.delay--;
            } else {
                if (task.stillSubscribed) {
                    if (task.task) {
                        task.runnable.run();
                        tasks.fastRemove(i);
                    } else if (tickCount >= task.lastTick) {
                        if (task.remove.getAsBoolean()) {
                            task.stillSubscribed = false;
                        } else {
                            task.runnable.run();
                            task.lastTick = tickCount + task.cycle;
                        }
                    }
                } else {
                    tasks.fastRemove(i);
                }
            }
        }
    }

    // clean up here
    public static void onWorldUnLoad(Level level) {
        FastObjectArrayList<TaskRunnableEntry> tasks = ILevel.getCapability(level, TaskHandler.class);
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

    public static void enqueueTask(Level level, Runnable task, int delay) {
        var entry = new TaskRunnableEntry(task, TaskRunnableEntry.FALSE, true, delay);
        var list = ILevel.getTasks(level);
        synchronized (list) {
            list.add(entry);
        }
    }

    public static TickableSubscription enqueueTick(Level level, BooleanSupplier isRemove, Runnable runnable, int cycle, int delay) {
        var entry = new TaskRunnableEntry(runnable, isRemove, false, delay);
        entry.cycle = cycle;
        var list = ILevel.getTasks(level);
        synchronized (list) {
            list.add(entry);
            return entry;
        }
    }

    public static TickableSubscription enqueueTick(Level level, Runnable runnable, int cycle, int delay) {
        return enqueueTick(level, TaskRunnableEntry.FALSE, runnable, cycle, delay);
    }
}
