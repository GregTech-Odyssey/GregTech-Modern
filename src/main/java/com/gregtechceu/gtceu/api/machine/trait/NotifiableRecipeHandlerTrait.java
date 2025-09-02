package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

public abstract class NotifiableRecipeHandlerTrait<T> extends MachineTrait implements IRecipeHandlerTrait<T> {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(NotifiableRecipeHandlerTrait.class);
    protected List<Runnable> listeners = new ObjectArrayList<>();
    @Persisted
    protected boolean isDistinct;

    protected Runnable notify;

    public NotifiableRecipeHandlerTrait(MetaMachine machine) {
        super(machine);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void onMachineUnLoad() {
        super.onMachineUnLoad();
        listeners.clear();
    }

    @Override
    public ISubscription addChangedListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void notifyListeners() {
        if (notify == null) {
            notify = () -> {
                listeners.forEach(Runnable::run);
                notify = null;
            };
            if (machine.getLevel() instanceof ServerLevel serverLevel) {
                TaskHandler.enqueueServerTask(serverLevel, notify, 0);
            }
        }
    }

    public boolean isDistinct() {
        return this.isDistinct;
    }

    public void setDistinct(final boolean isDistinct) {
        this.isDistinct = isDistinct;
    }
}
