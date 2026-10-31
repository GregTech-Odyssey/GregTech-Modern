package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;

public class ControlPartMachine extends MultiblockPartMachine implements IControllable {

    private static final Component MIN_STRENGTH = Component.translatable("cover.machine_controller.min_strength");
    private static final Component INVERTED = Component.translatable("cover.machine_controller.inverted");
    private static final Component INVERTED_TIP = Component.translatable("cover.machine_controller.inverted.tooltip");
    private static final Component REDSTONE_MODE = Component.translatable("cover.machine_controller.redstone_mode");
    private static final Component REDSTONE_MODE_TIP = Component.translatable("cover.machine_controller.redstone_mode.tooltip");

    @SaveToDisk
    @SyncToClient
    private boolean isInverted = false;
    @SaveToDisk
    @SyncToClient
    private int minRedstoneStrength = 1;
    @SaveToDisk
    @SyncToClient
    private boolean isRed = false;

    @SaveToDisk
    protected boolean enabled;

    public ControlPartMachine(MetaMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public boolean isWorkingEnabled() {
        return enabled;
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        enabled = isWorkingAllowed;
        for (var controller : getControllers()) {
            if (controller instanceof IControllable controllable) {
                controllable.setWorkingEnabled(isWorkingAllowed);
            }
        }
    }

    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        if (getLevel() instanceof ServerLevel serverLevel) {
            TaskHandler.enqueueTask(serverLevel, this::updateInput, 0);
        }
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateInput();
    }

    public void setMinRedstoneStrength(int minRedstoneStrength) {
        this.minRedstoneStrength = minRedstoneStrength;
        updateInput();
    }

    public void setInverted(boolean inverted) {
        isInverted = inverted;
        updateInput();
    }

    public void setRed(boolean isRed) {
        this.isRed = isRed;
        updateInput();
    }

    private void updateInput() {
        if (isRed) {
            Level level = getLevel();
            if (level == null || level.isClientSide) return;
            boolean shouldAllowWorking = level.getSignal(getPos().relative(getFrontFacing()), getFrontFacing()) < minRedstoneStrength;
            setWorkingEnabled(isInverted != shouldAllowWorking);
        }
    }

    @Override
    public Widget createUIWidget() {
        var page = Form.page();
        page.addChild(Form.numberRow(MIN_STRENGTH,
                NumberField.ofInt(UISizes.CONTENT_WIDTH, () -> minRedstoneStrength, this::setMinRedstoneStrength, 1, 15)));
        page.addChild(Form.controlRow(INVERTED, Switch.of(() -> isInverted, this::setInverted), INVERTED_TIP));
        page.addChild(Form.controlRow(REDSTONE_MODE, Switch.of(() -> isRed, this::setRed), REDSTONE_MODE_TIP));
        return page;
    }
}
