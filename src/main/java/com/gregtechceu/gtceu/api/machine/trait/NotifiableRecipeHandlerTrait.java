package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public abstract class NotifiableRecipeHandlerTrait<T> extends MachineTrait implements IRecipeHandlerTrait<T> {

    protected List<Runnable> listeners = new ObjectArrayList<>();
    @Getter
    @Setter
    @Persisted
    protected boolean isDistinct;

    protected Runnable notify;

    public NotifiableRecipeHandlerTrait(MetaMachine machine) {
        super(machine);
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        notify = null;
    }

    @Override
    public void onMachineUnLoad() {
        super.onMachineUnLoad();
        notify = null;
    }

    @Override
    public ISubscription addChangedListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void notifyListeners() {
        if (notify == null) {
            if (machine.getLevel() instanceof ServerLevel serverLevel) {
                notify = () -> listeners.forEach(Runnable::run);
                TaskHandler.enqueueServerTask(serverLevel, notify, () -> notify = null, 0);
            }
        }
    }
}
