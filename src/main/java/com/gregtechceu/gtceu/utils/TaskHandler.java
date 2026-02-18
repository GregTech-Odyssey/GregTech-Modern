package com.gregtechceu.gtceu.utils;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.core.ILevel;
import com.gregtechceu.gtceu.utils.collection.FastObjectArrayList;

import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

public class TaskHandler {

    public static boolean isAsyncService() {
        return AsyncTask.IN_SERVICE.get();
    }

    public static int getTickCount(TaskHandler handler) {
        if (handler instanceof AsyncTask asyncTask) return asyncTask.tickCount;
        var server = GTCEu.getMinecraftServer();
        if (server != null) return server.getTickCount();
        return GTValues.CLIENT_TIME;
    }

    public static TaskHandler createAsync(ScheduledExecutorService service, long period) {
        return new AsyncTask(service, period);
    }

    public static TaskHandler create() {
        return new TaskHandler();
    }

    private ObjectArrayList<TaskRunnableEntry> waitingTasks = new ObjectArrayList<>();

    private FastObjectArrayList<TaskRunnableEntry> tasks = new FastObjectArrayList<>();

    TaskHandler() {}

    public static void onTickUpdate(Level level, int tickCount) {
        ((ILevel) level).gtceu$getTaskHandler().runTask(tickCount);
    }

    public static void onWorldUnLoad(Level level) {
        ((ILevel) level).gtceu$getTaskHandler().unsubscribe();
        ((ILevel) level).gtceu$getAsyncTaskHandler().unsubscribe();
    }

    public static void enqueueTask(Level level, Runnable task, int delay) {
        ((ILevel) level).gtceu$getTaskHandler().enqueueTask(task, delay);
    }

    public static TickableSubscription enqueueTick(Level level, @Nullable TickableSubscription subscription, BooleanSupplier isRemove, Runnable runnable, int cycle, int delay) {
        return ((ILevel) level).gtceu$getTaskHandler().enqueueTick(subscription, isRemove, runnable, cycle, delay);
    }

    public static TickableSubscription enqueueTick(Level level, BooleanSupplier isRemove, Runnable runnable, int cycle, int delay) {
        return ((ILevel) level).gtceu$getTaskHandler().enqueueTick(isRemove, runnable, cycle, delay);
    }

    public static TickableSubscription enqueueTick(Level level, Runnable runnable, int cycle, int delay) {
        return ((ILevel) level).gtceu$getTaskHandler().enqueueTick(TaskRunnableEntry.FALSE, runnable, cycle, delay);
    }

    public static void enqueueAsyncTask(Level level, Runnable task, int delay) {
        ((ILevel) level).gtceu$getAsyncTaskHandler().enqueueTask(task, delay);
    }

    public static TickableSubscription enqueueAsyncTick(Level level, @Nullable TickableSubscription subscription, BooleanSupplier isRemove, Runnable runnable, int cycle, int delay) {
        return ((ILevel) level).gtceu$getAsyncTaskHandler().enqueueTick(subscription, isRemove, runnable, cycle, delay);
    }

    public static TickableSubscription enqueueAsyncTick(Level level, BooleanSupplier isRemove, Runnable runnable, int cycle, int delay) {
        return ((ILevel) level).gtceu$getAsyncTaskHandler().enqueueTick(isRemove, runnable, cycle, delay);
    }

    public static TickableSubscription enqueueAsyncTick(Level level, Runnable runnable, int cycle, int delay) {
        return ((ILevel) level).gtceu$getAsyncTaskHandler().enqueueTick(TaskRunnableEntry.FALSE, runnable, cycle, delay);
    }

    void runTask(int tickCount) {
        synchronized (this) {
            if (!waitingTasks.isEmpty()) {
                tasks.addAll(waitingTasks);
                waitingTasks.clear();
            }
            if (tasks.isEmpty()) return;
        }
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

    public void unsubscribe() {
        synchronized (this) {
            waitingTasks.forEach(TickableSubscription::unsubscribe);
            waitingTasks = new ObjectArrayList<>();
        }
        tasks.forEach(TickableSubscription::unsubscribe);
        tasks = new FastObjectArrayList<>();
    }

    public void enqueueTask(Runnable task, int delay) {
        var entry = new TaskRunnableEntry(task, TaskRunnableEntry.FALSE, true, delay);
        synchronized (this) {
            waitingTasks.add(entry);
        }
    }

    public TickableSubscription enqueueTick(BooleanSupplier isRemove, Runnable runnable, int cycle, int delay) {
        var entry = new TaskRunnableEntry(runnable, isRemove, false, delay);
        entry.cycle = cycle;
        synchronized (this) {
            waitingTasks.add(entry);
            return entry;
        }
    }

    public TickableSubscription enqueueTick(@Nullable TickableSubscription subscription, BooleanSupplier isRemove, Runnable runnable, int cycle, int delay) {
        if (subscription == null || !subscription.stillSubscribed) {
            return enqueueTick(isRemove, runnable, cycle, delay);
        }
        return subscription;
    }

    public TickableSubscription enqueueTick(@Nullable TickableSubscription subscription, Runnable runnable, int cycle, int delay) {
        return enqueueTick(subscription, TaskRunnableEntry.FALSE, runnable, cycle, delay);
    }

    private static final class AsyncTask extends TaskHandler {

        private static final ThreadLocal<Boolean> IN_SERVICE = ThreadLocal.withInitial(() -> false);

        private ScheduledFuture<?> scheduledFuture;
        private final Lock lock = new ReentrantLock();
        private int tickCount;
        private final ScheduledExecutorService service;
        private final long period;

        private AsyncTask(ScheduledExecutorService service, long period) {
            this.service = service;
            this.period = period;
        }

        private void createExecutorService() {
            if (scheduledFuture == null) scheduledFuture = service.scheduleAtFixedRate(this::tick, 0, period, TimeUnit.MILLISECONDS);
        }

        @Override
        public void unsubscribe() {
            lock.lock();
            try {
                if (scheduledFuture != null) {
                    scheduledFuture.cancel(false);
                    scheduledFuture = null;
                }
                super.unsubscribe();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void enqueueTask(Runnable task, int delay) {
            super.enqueueTask(task, delay);
            createExecutorService();
        }

        @Override
        public TickableSubscription enqueueTick(BooleanSupplier isRemove, Runnable runnable, int cycle, int delay) {
            var entry = super.enqueueTick(isRemove, runnable, cycle, delay);
            createExecutorService();
            return entry;
        }

        private void tick() {
            lock.lock();
            try {
                IN_SERVICE.set(true);
                runTask(tickCount);
            } catch (Throwable e) {
                GTCEu.LOGGER.error("Error while AsyncTask: {}", e.getMessage());
                e.printStackTrace();
            } finally {
                lock.unlock();
                tickCount++;
                IN_SERVICE.set(false);
            }
        }
    }

    private static final class TaskRunnableEntry extends TickableSubscription {

        private static final BooleanSupplier FALSE = () -> false;

        private final BooleanSupplier remove;
        private final boolean task;
        private int delay;

        private TaskRunnableEntry(Runnable runnable, BooleanSupplier remove, boolean task, int delay) {
            super(runnable);
            this.remove = remove;
            this.task = task;
            this.delay = delay;
        }
    }
}
