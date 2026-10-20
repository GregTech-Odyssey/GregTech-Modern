package com.gregtechceu.gtceu.api.cover;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

public interface IMachineShutdownListener {

    void onMachineShutdown();

    static void notify(MetaMachine machine) {
        for (var cover : machine.getCoverContainer().getCovers()) {
            if (cover instanceof IMachineShutdownListener listener) listener.onMachineShutdown();
        }
    }
}
