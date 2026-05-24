package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

public abstract class NotifiableRecipeHandlerTrait extends MachineTrait implements IRecipeHandlerTrait {

    protected List<Runnable> listeners = new ArrayList<>();

    protected boolean isDirty = true;

    public NotifiableRecipeHandlerTrait(MetaMachine machine) {
        super(machine);
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        isDirty = true;
    }

    @Override
    public void onMachineUnLoad() {
        super.onMachineUnLoad();
        isDirty = true;
    }

    @Override
    public ISubscription addChangedListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    protected void runNotify() {
        listeners.forEach(Runnable::run);
        isDirty = true;
    }

    public void notifyListeners() {
        if (isDirty) {
            if (machine.getLevel() instanceof ServerLevel serverLevel) {
                isDirty = false;
                serverLevel.getServer().tell(new TickTask(0, this::runNotify));
            }
        }
    }
}
