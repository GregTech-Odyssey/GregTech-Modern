package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.content.Circuits;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;

import appeng.api.stacks.AEItemKey;
import org.jetbrains.annotations.NotNull;

public class CircuitHandler extends NotifiableInventory<AEItemKey> {

    public static NotifiableInventory<AEItemKey> create(MetaMachine machine) {
        return new CircuitHandler(machine);
    }

    protected CircuitHandler(MetaMachine machine, @NotNull IO capabilityIO) {
        super(machine, KeyInventory.items(1), IO.IN, capabilityIO);
        setFilter(k -> k instanceof AEItemKey key && key.getItem() == Circuits.item());
    }

    protected CircuitHandler(MetaMachine machine) {
        this(machine, IO.NONE);
    }

    @Override
    public boolean isNotConsumable() {
        return true;
    }

    @Override
    public boolean isPresenceOnly() {
        return true;
    }

    public int getConfiguration() {
        return Circuits.get(storage, 0);
    }

    public void setConfiguration(int configuration) {
        Circuits.set(storage, 0, configuration);
    }
}
