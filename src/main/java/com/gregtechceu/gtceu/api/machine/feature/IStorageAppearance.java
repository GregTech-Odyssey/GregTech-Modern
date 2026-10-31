package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.machine.trait.StorageAppearance;

/** Storage machines with a configurable casing texture. */
public interface IStorageAppearance extends IFancyUIMachine {

    StorageAppearance getStorageAppearance();

    static void attach(ConfiguratorPanel panel, IFancyUIProvider provider) {
        if (provider instanceof IStorageAppearance storage) panel.attachConfigurators(storage.getStorageAppearance());
    }
}
