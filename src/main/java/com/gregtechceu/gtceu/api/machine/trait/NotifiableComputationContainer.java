package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationHatch;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public class NotifiableComputationContainer extends NotifiableRecipeHandlerTrait implements ICapabilityTrait, IOpticalComputationProvider {

    @Setter
    @Getter
    protected Predicate<@Nullable Direction> capabilityValidator = GTUtil.FAVORABLE;

    protected final boolean transmitter;
    protected boolean call;

    public NotifiableComputationContainer(MetaMachine machine, boolean transmitter) {
        super(machine);
        this.transmitter = transmitter;
    }

    @Override
    public long requestCWU(long cwu, boolean simulate) {
        if (call) return 0;
        call = true;
        var result = 0L;
        if (transmitter) {
            if (machine instanceof IMultiPart part) {
                for (IMultiController controller : part.getControllers()) {
                    if (!controller.isFormed()) {
                        continue;
                    }
                    if (controller instanceof IOpticalComputationProvider provider) {
                        result += provider.requestCWU(cwu, simulate);
                    }
                }
            }
        } else {
            // Ask the attached Transmitter hatch, if it exists
            IOpticalComputationProvider provider = getOpticalNetProvider();
            if (provider != null) {
                result = provider.requestCWU(cwu, simulate);
            }
        }
        call = false;
        return result;
    }

    @Override
    public boolean canBridge() {
        if (transmitter) {
            if (machine instanceof IMultiPart part) {
                for (IMultiController controller : part.getControllers()) {
                    if (!controller.isFormed()) {
                        continue;
                    }
                    if (controller instanceof IOpticalComputationProvider provider) return provider.canBridge();
                }
            }
        } else {
            // Ask the attached Transmitter hatch, if it exists
            IOpticalComputationProvider provider = getOpticalNetProvider();
            if (provider == null) return true; // nothing found, so don't report a problem, just pass quietly
            return provider.canBridge();
        }
        return false;
    }

    @Nullable
    protected IOpticalComputationProvider getOpticalNetProvider() {
        var direction = machine.getFrontFacing();
        BlockEntity blockEntity = machine.getNeighbor(direction);
        if (blockEntity != null) {
            var cap = blockEntity.getCapability(GTCapability.CAPABILITY_COMPUTATION_PROVIDER, direction.getOpposite()).orElse(null);
            if (cap instanceof IOpticalComputationHatch hatch) {
                return hatch.isTransmitter() ? cap : null;
            } else {
                return cap;
            }
        }
        return null;
    }

    public IO getHandlerIO() {
        return transmitter ? IO.NONE : IO.IN;
    }

    @Override
    public IO getCapabilityIO() {
        return getHandlerIO();
    }
}
