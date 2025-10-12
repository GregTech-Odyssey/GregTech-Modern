package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;

import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class ControlPartMachine extends MultiblockPartMachine implements IControllable {

    @Persisted
    private boolean isInverted = false;
    @Persisted
    private int minRedstoneStrength = 1;
    @Persisted
    private boolean isRed = false;

    @Persisted
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
            serverLevel.getServer().tell(new TickTask(0, this::updateInput));
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
        WidgetGroup group = new WidgetGroup(0, 0, 176, 75);
        group.addWidget(new LabelWidget(10, 5, "cover.machine_controller.title"));
        group.addWidget(new IntInputWidget(10, 50, 131, 20, () -> minRedstoneStrength, this::setMinRedstoneStrength).setMin(1).setMax(15));
        group.addWidget(new ToggleButtonWidget(146, 50, 20, 20, GuiTextures.INVERT_REDSTONE_BUTTON, () -> isInverted, this::setInverted).isMultiLang().setTooltipText("cover.machine_controller.invert"));
        group.addWidget(new ToggleButtonWidget(20, 20, 20, 20, GuiTextures.INVERT_REDSTONE_BUTTON, () -> isRed, this::setRed).setTooltipText("gui.tooltips.redstone_mode"));
        return group;
    }
}
