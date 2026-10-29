package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.StorageAppearance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Storage machines with a configurable casing texture. */
public interface IStorageAppearance extends IFancyUIMachine {

    StorageAppearance getStorageAppearance();

    static void attach(ConfiguratorPanel panel, IFancyUIProvider provider) {
        if (provider instanceof IStorageAppearance storage) panel.attachConfigurators(storage.getStorageAppearance());
    }
}
