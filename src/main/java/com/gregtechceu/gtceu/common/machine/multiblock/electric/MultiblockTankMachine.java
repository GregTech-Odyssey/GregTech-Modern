package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.fluids.PropertyFluidFilter;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidAdapter;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyHandlerView;

import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.annotations.SaveToDisk;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class MultiblockTankMachine extends MultiblockControllerMachine implements IFancyUIMachine {

    @Getter
    @SaveToDisk
    @NotNull
    private final NotifiableInventory<AEFluidKey> tank;
    private final IKeyHandler<AEFluidKey> fluidHandler;
    private boolean handlerLocked = true;

    public MultiblockTankMachine(MetaMachineBlockEntity holder, int capacity, @Nullable PropertyFluidFilter filter, Object... args) {
        super(holder);
        this.tank = createTank(capacity, filter, args);
        fluidHandler = new KeyHandlerView<>(tank) {

            @Override
            protected boolean canInsert(AEFluidKey key) {
                return !handlerLocked;
            }

            @Override
            protected boolean canExtract(AEFluidKey key) {
                return !handlerLocked;
            }
        };
    }

    protected NotifiableInventory<AEFluidKey> createTank(int capacity, @Nullable PropertyFluidFilter filter, Object... args) {
        var fluidTank = NotifiableInventory.fluids(this, 1, capacity, IO.BOTH);
        if (filter != null) fluidTank.setFilter(k -> k instanceof AEFluidKey fluidKey && filter.test(fluidKey.getReadOnlyStack()));
        return fluidTank;
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return isFormed();
    }

    @Override
    @Nullable
    public IKeyHandler<AEItemKey> getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return null;
    }

    @Override
    @Nullable
    public IKeyHandler<AEFluidKey> getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return fluidHandler;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        handlerLocked = false;
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        handlerLocked = true;
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
        group.addWidget(new TankWidget(new ForgeFluidAdapter(tank.storage), 0, 68, 23, true, true).setBackground(GuiTextures.FLUID_SLOT));
        group.addWidget(new ToggleButtonWidget(6, 40, 18, 18, GuiTextures.BUTTON_VOID, () -> tank.isVoiding, b -> tank.isVoiding = b).setShouldUseBaseBackground().setTooltipText("gtceu.gui.fluid_voiding_partial.tooltip"));
        return group;
    }

    private String getFluidLabel() {
        return String.valueOf(tank.storage.amountAt(0));
    }
}
