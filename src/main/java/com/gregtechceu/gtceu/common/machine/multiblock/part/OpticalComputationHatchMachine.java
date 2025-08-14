package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationHatch;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableComputationContainer;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class OpticalComputationHatchMachine extends MultiblockPartMachine implements IOpticalComputationHatch {

    private final boolean transmitter;
    protected NotifiableComputationContainer computationContainer;

    public OpticalComputationHatchMachine(MetaMachineBlockEntity holder, boolean transmitter) {
        super(holder);
        this.transmitter = transmitter;
        this.computationContainer = createComputationContainer(transmitter);
    }

    protected NotifiableComputationContainer createComputationContainer(Object... args) {
        if (args.length > 0 && args[args.length - 1] instanceof Boolean transmitter) {
            return new NotifiableComputationContainer(this, transmitter);
        }
        throw new IllegalArgumentException();
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }

    @Override
    public boolean canShared() {
        return false;
    }

    public boolean isTransmitter() {
        return this.transmitter;
    }

    @Override
    public long requestCWU(long cwu, boolean simulate) {
        return computationContainer.requestCWU(cwu, simulate);
    }

    @Override
    public boolean canBridge() {
        return computationContainer.canBridge();
    }
}
