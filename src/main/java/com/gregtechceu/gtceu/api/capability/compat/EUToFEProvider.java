package com.gregtechceu.gtceu.api.capability.compat;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTMath;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.LazyOptionalUtil;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import org.jetbrains.annotations.NotNull;

public class EUToFEProvider extends CapabilityCompatProvider {

    public EUToFEProvider(BlockEntity tileEntity) {
        super(tileEntity);
    }

    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, Direction facing) {
        if (!ConfigHolder.INSTANCE.compat.energy.nativeEUToFE || capability != GTCapability.CAPABILITY_ENERGY_CONTAINER)
            return LazyOptional.empty();

        IEnergyStorage energyStorage = LazyOptionalUtil.get(getUpvalueCapability(ForgeCapabilities.ENERGY, facing));
        return energyStorage != null ? GTCapability.CAPABILITY_ENERGY_CONTAINER.orEmpty(capability, LazyOptional.of(() -> new GTEnergyWrapper(energyStorage))) : LazyOptional.empty();
    }

    public static class GTEnergyWrapper implements IEnergyContainer {

        private final IEnergyStorage energyStorage;

        public GTEnergyWrapper(IEnergyStorage energyStorage) {
            this.energyStorage = energyStorage;
        }

        @Override
        public long acceptEnergyFromNetwork(Object o, Direction facing, long voltage, long energyToAdd) {
            int receive = energyStorage.receiveEnergy(GTMath.saturatedCast(FeCompat.toFeLong(energyToAdd, FeCompat.ratio(false))), false);
            return FeCompat.toEu(receive, FeCompat.ratio(true));
        }

        @Override
        public long changeEnergy(long delta) {
            if (delta == 0) return 0;
            else if (delta < 0) return FeCompat.extractEu(energyStorage, -delta, false);
            else return FeCompat.insertEu(energyStorage, delta, false);
        }

        @Override
        public long getEnergyCapacity() {
            return FeCompat.toEu(energyStorage.getMaxEnergyStored(), FeCompat.ratio(false));
        }

        @Override
        public long getEnergyStored() {
            return FeCompat.toEu(energyStorage.getEnergyStored(), FeCompat.ratio(false));
        }

        /**
         * Most RF/FE cables blindly try to insert energy without checking if there is space, since the receiving
         * IEnergyStorage should handle it.
         * This simulates that behavior in most places by allowing our "is there space" checks to pass and letting the
         * cable attempt to insert energy.
         * If the wrapped TE actually cannot accept any more energy, the energy transfer will return 0 before any
         * changes to our internal rf buffer.
         */
        @Override
        public long getEnergyCanBeInserted() {
            return Math.max(1, getEnergyCapacity() - getEnergyStored());
        }

        @Override
        public long getInputAmperage() {
            return getInputVoltage() == 0 ? 0 : 2;
        }

        @Override
        public long getInputVoltage() {
            long maxInput = energyStorage.receiveEnergy(Integer.MAX_VALUE, true);

            if (maxInput == 0) return 0;
            return GTValues.V[GTUtil
                    .getTierByVoltage(FeCompat.toEu(maxInput, FeCompat.ratio(false)))];
        }

        @Override
        public boolean inputsEnergy(Direction facing) {
            return energyStorage.canReceive();
        }

        /**
         * Wrapped FE-consumers should not be able to output EU.
         */
        @Override
        public boolean outputsEnergy(Direction facing) {
            return false;
        }

        /**
         * Hide this BlockEntity EU-capability in TOP. Allows FE-machines to
         * "silently" accept EU without showing their charge in EU in TOP.
         * Let the machine display it in FE instead, however it chooses to.
         */
        @Override
        public boolean isOneProbeHidden() {
            return true;
        }
    }
}
