package com.gregtechceu.gtceu.common.machine.multiblock.primitive;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.fluids.PropertyFluidFilter;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidAdapter;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyHandlerView;
import com.gregtechceu.gtceu.uipro.elements.FluidSlot;
import com.gregtechceu.gtceu.uipro.elements.IconToggle;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.uiwidgets.inventory.HatchViews;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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

    public MultiblockTankMachine(MetaMachineBlockEntity holder, long capacity, @Nullable PropertyFluidFilter filter, Object... args) {
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

    protected NotifiableInventory<AEFluidKey> createTank(long capacity, @Nullable PropertyFluidFilter filter, Object... args) {
        var fluidTank = NotifiableInventory.fluids(this, 1, capacity, IO.BOTH);
        if (filter != null) fluidTank.setFilter(k -> k instanceof AEFluidKey fluidKey && filter.test(fluidKey));
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

    private static final Component FLUID_AMOUNT = Component.translatable("gtceu.gui.fluid_amount");
    private static final Component FLUID_VOIDING_ENABLED = Component.translatable("gtceu.gui.fluid_voiding_partial.tooltip.enabled");
    private static final Component FLUID_VOIDING_DISABLED = Component.translatable("gtceu.gui.fluid_voiding_partial.tooltip.disabled");

    /////////////////////////////////////
    // *********** GUI ***********//
    /////////////////////////////////////
    @Override
    public Widget createUIWidget() {
        var slot = FluidSlot.of(new ForgeFluidAdapter(tank.storage), 0, true, true);
        var voiding = IconToggle.of(WidgetIcons.VOID_OVERFLOW, () -> tank.isVoiding, b -> tank.isVoiding = b)
                .onOffTooltips(FLUID_VOIDING_ENABLED, FLUID_VOIDING_DISABLED);
        var operations = HatchViews.operations(slot, voiding);
        return HatchViews.page(operations);
    }
}
