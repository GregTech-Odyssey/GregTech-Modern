package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IWailaDisplayProvider;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.UpdateListener;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import lombok.Getter;
import lombok.Setter;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import javax.annotation.ParametersAreNonnullByDefault;

@Getter
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class TransformerMachine extends TieredEnergyMachine implements IControllable, IWailaDisplayProvider {

    @Persisted
    @DescSynced
    @UpdateListener(methodName = "onTransformUpdated")
    private boolean isTransformUp;
    @Setter
    @Persisted
    private boolean isWorkingEnabled;
    private final int baseAmp;

    public TransformerMachine(MetaMachineBlockEntity holder, int tier, int baseAmp, Object... args) {
        super(holder, tier, baseAmp, args);
        this.isWorkingEnabled = true;
        this.baseAmp = baseAmp;
    }

    @SuppressWarnings("unused")
    private void onTransformUpdated(boolean newValue, boolean oldValue) {
        scheduleRenderUpdate();
        updateEnergyContainer(newValue);
    }

    @Override
    protected NotifiableEnergyContainer createEnergyContainer(Object... args) {
        var amp = (args.length > 0 && args[0] instanceof Integer a) ? a : 1;
        NotifiableEnergyContainer energyContainer;
        long tierVoltage = GTValues.V[getTier()];
        // Since this.baseAmp is not yet initialized, we substitute with 1A as default
        energyContainer = new NotifiableEnergyContainer(this, tierVoltage * 8L, tierVoltage * 4, amp, tierVoltage, 4L * amp);
        energyContainer.setSideInputCondition(s -> s == getFrontFacing() && isWorkingEnabled());
        energyContainer.setSideOutputCondition(s -> s != getFrontFacing() && isWorkingEnabled());
        return energyContainer;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateEnergyContainer(isTransformUp);
    }

    public void updateEnergyContainer(boolean isTransformUp) {
        long tierVoltage = GTValues.V[getTier()];
        int lowAmperage = baseAmp * 4;
        if (isTransformUp) {
            // storage = n amp high; input = tier / 4; amperage = 4n; output = tier; amperage = n
            this.energyContainer.resetBasicInfo(tierVoltage * 8L * lowAmperage, tierVoltage, lowAmperage, tierVoltage * 4, baseAmp);
            energyContainer.setSideInputCondition(s -> s != getFrontFacing() && isWorkingEnabled());
            energyContainer.setSideOutputCondition(s -> s == getFrontFacing() && isWorkingEnabled());
        } else {
            // storage = n amp high; input = tier; amperage = n; output = tier / 4; amperage = 4n
            this.energyContainer.resetBasicInfo(tierVoltage * 8L * lowAmperage, tierVoltage * 4, baseAmp, tierVoltage, lowAmperage);
            energyContainer.setSideInputCondition(s -> s == getFrontFacing() && isWorkingEnabled());
            energyContainer.setSideOutputCondition(s -> s != getFrontFacing() && isWorkingEnabled());
        }
    }

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            // frontTexture
            return GTValues.VC[getTier() + 1];
        } else if (index == 3) {
            // otherTexture
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }

    //////////////////////////////////////
    // ****** Interaction *******//
    //////////////////////////////////////
    public void setTransformUp(boolean isTransformUp) {
        if (this.isTransformUp != isTransformUp && !isRemote()) {
            this.isTransformUp = isTransformUp;
            updateEnergyContainer(isTransformUp);
        }
    }

    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        if (!isRemote()) {
            setTransformUp(!isTransformUp());
            playerIn.sendSystemMessage(Component.translatable(isTransformUp() ? "gtceu.machine.transformer.message_transform_up" : "gtceu.machine.transformer.message_transform_down", energyContainer.getInputVoltage(), energyContainer.getInputAmperage(), energyContainer.getOutputVoltage(), energyContainer.getOutputAmperage()));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendWailaTooltip(CompoundTag data, ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        boolean transformUp = data.getBoolean("transformUp");
        int voltage = data.getInt("baseVoltage");
        int amp = data.getInt("baseAmp");
        if (transformUp) {
            iTooltip.add(Component.translatable("gtceu.top.transform_up",
                    (GTValues.VNF[voltage] + " §r(" + amp * 4 + "A) -> " + GTValues.VNF[voltage + 1] + " §r(" +
                            amp +
                            "A)")));
        } else {
            iTooltip.add(Component.translatable("gtceu.top.transform_down",
                    (GTValues.VNF[voltage + 1] + " §r(" + amp + "A) -> " + GTValues.VNF[voltage] + " §r(" +
                            amp * 4 +
                            "A)")));
        }

        if (blockAccessor.getHitResult().getDirection() ==
                Direction.from3DDataValue(data.getInt("side"))) {
            iTooltip.add(
                    Component.translatable(
                            (transformUp ? "gtceu.top.transform_output" : "gtceu.top.transform_input"),
                            (GTValues.VNF[voltage + 1] + " §r(" + amp + "A)")));
        } else {
            iTooltip.add(
                    Component.translatable(
                            (transformUp ? "gtceu.top.transform_input" : "gtceu.top.transform_output"),
                            (GTValues.VNF[voltage] + " §r(" + amp * 4 + "A)")));
        }
    }

    @Override
    public void appendWailaData(CompoundTag data, BlockAccessor blockAccessor) {
        data.putInt("side", getFrontFacing().get3DDataValue());
        data.putBoolean("transformUp", isTransformUp);
        data.putInt("baseAmp", baseAmp);
        data.putInt("baseVoltage", getTier());
    }
}
