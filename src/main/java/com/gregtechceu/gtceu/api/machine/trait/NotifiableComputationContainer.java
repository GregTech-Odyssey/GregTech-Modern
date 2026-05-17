package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationHatch;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IO;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NotifiableComputationContainer extends NotifiableRecipeHandlerTrait implements IOpticalComputationProvider {

    protected boolean transmitter;
    protected long lastTimeStamp;
    protected long currentOutputCwu = 0;
    protected long lastOutputCwu = 0;
    protected boolean call;

    public NotifiableComputationContainer(MetaMachine machine, boolean transmitter) {
        super(machine);
        this.transmitter = transmitter;
        this.lastTimeStamp = Long.MIN_VALUE;
    }

    @Override
    public long requestCWU(long cwu, boolean simulate) {
        if (call) return 0;
        call = true;
        var result = 0L;
        var latestTimeStamp = getMachine().getOffsetTimer();
        if (lastTimeStamp < latestTimeStamp) {
            lastOutputCwu = currentOutputCwu;
            currentOutputCwu = 0;
            lastTimeStamp = latestTimeStamp;
        }
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
    public long getMaxCWU() {
        if (call) return 0;
        call = true;
        var result = 0L;
        var latestTimeStamp = getMachine().getOffsetTimer();
        if (lastTimeStamp < latestTimeStamp) {
            lastOutputCwu = currentOutputCwu;
            currentOutputCwu = 0;
            lastTimeStamp = latestTimeStamp;
        }
        if (transmitter) {
            if (machine instanceof IMultiPart part) {
                for (IMultiController controller : part.getControllers()) {
                    if (!controller.isFormed()) {
                        continue;
                    }
                    if (controller instanceof IOpticalComputationProvider provider) {
                        result += provider.getMaxCWU();
                    }
                }
            }
        } else {
            // Ask the attached Transmitter hatch, if it exists
            IOpticalComputationProvider provider = getOpticalNetProvider();
            if (provider != null) {
                result = provider.getMaxCWU();
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

    @Override
    public List<Long> handleRecipeInner(IO io, GTRecipe recipe, List<Long> left, boolean simulate) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public RecipeCapability<Long> getCapability() {
        return CWURecipeCapability.CAP;
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
}
