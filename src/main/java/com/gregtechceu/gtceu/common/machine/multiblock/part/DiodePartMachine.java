package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.recipe.handler.IO;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class DiodePartMachine extends TieredIOPartMachine {

    public static int MAX_AMPS = 16;
    @SaveToDisk
    protected NotifiableEnergyContainer energyContainer;
    @Getter
    @SyncToClient
    @SaveToDisk(key = "amp_mode")
    private int amps;

    public DiodePartMachine(MetaMachineBlockEntity holder, int tier) {
        super(holder, tier, IO.BOTH);
        long tierVoltage = GTValues.V[getTier()];
        this.amps = 1;
        this.energyContainer = new NotifiableEnergyContainer(this, tierVoltage * MAX_AMPS * 2, tierVoltage, MAX_AMPS, tierVoltage, MAX_AMPS);
        reinitializeEnergyContainer();
    }

    private void cycleAmpMode() {
        amps = amps == getMaxAmperage() ? 1 : amps << 1;
        if (!getLevel().isClientSide) {
            reinitializeEnergyContainer();
            notifyBlockUpdate();
            onChanged();
        }
    }

    /**
     * Change this value (or override) to make the Diode able to handle more amps. Must be a power of 2
     */
    protected int getMaxAmperage() {
        return MAX_AMPS;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!GTCEu.isClientThread()) reinitializeEnergyContainer();
    }

    @Override
    public @Nullable <T> Object getGTCapability(Class<T> cap, @Nullable Direction side) {
        if (cap == GTCapability.ENERGY_CONTAINER) {
            return energyContainer;
        }
        return super.getGTCapability(cap, side);
    }

    protected void reinitializeEnergyContainer() {
        long tierVoltage = GTValues.V[getTier()];
        this.energyContainer.resetBasicInfo(tierVoltage * MAX_AMPS * 2, tierVoltage, amps, tierVoltage, amps);
        this.energyContainer.setSideInputCondition(s -> s != getFrontFacing());
        this.energyContainer.setSideOutputCondition(s -> isWorkingEnabled());
    }

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }

    @Override
    public boolean isFacingValid(Direction facing) {
        return true;
    }

    @Override
    protected InteractionResult onSoftMalletClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        cycleAmpMode();
        if (getLevel().isClientSide) {
            scheduleRenderUpdate();
            return InteractionResult.CONSUME;
        }
        playerIn.sendSystemMessage(Component.translatable("gtceu.machine.diode.message", amps));
        return InteractionResult.CONSUME;
    }
}
