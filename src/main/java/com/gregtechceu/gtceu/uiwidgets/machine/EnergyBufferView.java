package com.gregtechceu.gtceu.uiwidgets.machine;

import com.gregtechceu.gtceu.api.machine.feature.IBufferedEnergyMachine;
import com.gregtechceu.gtceu.uipro.elements.ProgressBar;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.multiblock.MultiblockPage;

import net.minecraft.network.chat.Component;

/** Shared reserve readout for electric machine pages. */
public final class EnergyBufferView {

    public static final Component LABEL = Component.translatable("gtceu.gui.internal_energy");

    private EnergyBufferView() {}

    public static void addTo(MultiblockPage page, IBufferedEnergyMachine machine) {
        if (!machine.isEnergyBufferEnabled()) return;
        page.addBar(LABEL, UITheme::barEnergy, () -> progress(machine)).percent()
                .bindDetail(MultiblockPage.chargeText(machine::getEnergyBuffer, machine::getEnergyBufferDisplayCapacity));
    }

    private static ProgressBar.Progress progress(IBufferedEnergyMachine machine) {
        return new ProgressBar.Progress(machine.getEnergyBuffer(), Math.max(1, machine.getEnergyBufferDisplayCapacity()), 0);
    }
}
