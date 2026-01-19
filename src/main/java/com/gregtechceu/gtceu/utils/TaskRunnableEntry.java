package com.gregtechceu.gtceu.utils;

import com.gregtechceu.gtceu.api.machine.TickableSubscription;

import java.util.function.BooleanSupplier;

public class TaskRunnableEntry extends TickableSubscription {

    static final BooleanSupplier FALSE = () -> false;

    final BooleanSupplier remove;
    final boolean task;
    int delay;

    TaskRunnableEntry(Runnable runnable, BooleanSupplier remove, boolean task, int delay) {
        super(runnable);
        this.remove = remove;
        this.task = task;
        this.delay = delay;
    }
}
