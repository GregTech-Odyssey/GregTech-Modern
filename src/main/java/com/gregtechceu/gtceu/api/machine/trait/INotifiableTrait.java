package com.gregtechceu.gtceu.api.machine.trait;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import java.util.function.Consumer;

public interface INotifiableTrait {

    boolean isAvailable();

    ISubscription addChangedListener(Runnable listener);

    void notifyListeners();

    static void addListener(Object trait, Runnable listener, Consumer<ISubscription> consumer) {
        if (trait instanceof INotifiableTrait notifiableTrait && notifiableTrait.isAvailable()) {
            consumer.accept(notifiableTrait.addChangedListener(listener));
        }
    }
}
