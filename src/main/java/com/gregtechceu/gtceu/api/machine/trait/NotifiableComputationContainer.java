package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationHatch;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.handler.IO;

import org.jetbrains.annotations.Nullable;

public class NotifiableComputationContainer extends NotifiableRecipeHandlerTrait implements ICapabilityTrait, IOpticalComputationProvider {

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
        var cap = GTCapabilityHelper.getComputation(machine.getNeighbor(direction), direction.getOpposite());
        if (cap instanceof IOpticalComputationHatch hatch) {
            return hatch.isTransmitter() ? cap : null;
        } else {
            return cap;
        }
    }

    @Override
    public IO getHandlerIO() {
        return transmitter ? IO.NONE : IO.IN;
    }

    @Override
    public IO getCapabilityIO() {
        return transmitter ? IO.OUT : IO.IN;
    }
}
