package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.fluids.PropertyFluidFilter;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.api.transfer.fluid.LockableIFluidHandler;

import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class MultiblockTankMachine extends MultiblockControllerMachine implements IFancyUIMachine {

    @Persisted
    @NotNull
    private final NotifiableFluidTank tank;
    private final LockableIFluidHandler fluidHandler;

    public MultiblockTankMachine(MetaMachineBlockEntity holder, int capacity, @Nullable PropertyFluidFilter filter, Object... args) {
        super(holder);
        this.tank = createTank(capacity, filter, args);
        fluidHandler = new LockableIFluidHandler(tank).setLock(true);
    }

    protected NotifiableFluidTank createTank(int capacity, @Nullable PropertyFluidFilter filter, Object... args) {
        var fluidTank = new NotifiableFluidTank(this, 1, capacity, IO.BOTH);
        if (filter != null) fluidTank.setFilter(filter);
        return fluidTank;
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return isFormed();
    }

    @Override
    @Nullable
    public IItemHandlerModifiable getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return null;
    }

    @Override
    @Nullable
    public IFluidHandlerModifiable getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return fluidHandler;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        fluidHandler.setLock(false);
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        fluidHandler.setLock(true);
    }

    /////////////////////////////////////
    // *********** GUI ***********//
    /////////////////////////////////////
    @Override
    public Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 90, 63);
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        group.addWidget(new ImageWidget(4, 4, 82, 55, GuiTextures.DISPLAY));
        group.addWidget(new LabelWidget(8, 8, "gtceu.gui.fluid_amount"));
        group.addWidget(new LabelWidget(8, 18, this::getFluidLabel).setTextColor(-1).setDropShadow(true));
        group.addWidget(new TankWidget(tank.getStorages()[0], 68, 23, true, true).setBackground(GuiTextures.FLUID_SLOT));
        group.addWidget(new ToggleButtonWidget(6, 40, 18, 18, GuiTextures.BUTTON_VOID, () -> tank.isVoiding, b -> tank.isVoiding = b).setShouldUseBaseBackground().setTooltipText("gtceu.gui.fluid_voiding_partial.tooltip"));
        return group;
    }

    private String getFluidLabel() {
        return String.valueOf(tank.getFluidInTank(0).getAmount());
    }

    public NotifiableFluidTank getTank() {
        return this.tank;
    }
}
