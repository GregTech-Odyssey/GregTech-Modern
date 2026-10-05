package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputBoth;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.misc.TickTimeMonitor;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidAdapter;
import com.gregtechceu.gtceu.api.transfer.forge.MenuItemAdapter;
import com.gregtechceu.gtceu.common.data.GTTickTimeMonitors;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DualHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.StorageAccess;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class BufferMachine extends TieredMachine implements IMachineLife, IAutoOutputBoth, IFancyUIMachine {

    @Getter
    @SaveToDisk
    @SyncToClient(scheduleUpdate = true)
    @Nullable
    protected Direction outputFacingItems;
    @Getter
    @SaveToDisk
    @SyncToClient(scheduleUpdate = true)
    @Nullable
    protected Direction outputFacingFluids;
    @Getter
    @SaveToDisk(defaultValue = "false")
    @SyncToClient(scheduleUpdate = true)
    protected boolean autoOutputItems;
    @Getter
    @SaveToDisk(defaultValue = "false")
    @SyncToClient(scheduleUpdate = true)
    protected boolean autoOutputFluids;
    @Getter
    @SaveToDisk(defaultValue = "false")
    protected boolean allowInputFromOutputSideItems;
    @Getter
    @SaveToDisk(defaultValue = "false")
    protected boolean allowInputFromOutputSideFluids;
    @Getter
    @SaveToDisk
    protected final NotifiableInventory<AEItemKey> inventory;
    @Getter
    @SaveToDisk
    protected final NotifiableInventory<AEFluidKey> tank;
    @Nullable
    protected TickableSubscription autoOutputSubs;
    protected final TickTimeMonitor autoOutputMonitor = holder.monitorTick(GTTickTimeMonitors.AUTO_OUTPUT, this::autoOutput);
    @Nullable
    protected ISubscription invSubs;
    @Nullable
    protected ISubscription tankSubs;

    public BufferMachine(MetaMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier);
        this.inventory = createInventory(args);
        this.tank = createTank(args);
    }

    ////////////////////////////////
    // ***** Initialization ******//
    ////////////////////////////////

    public static int getInventorySize(int tier) {
        return (int) Math.pow(tier + 2, 2);
    }

    public static int getTankSize(int tier) {
        return tier + 2;
    }

    protected NotifiableInventory<AEItemKey> createInventory(Object... args) {
        return NotifiableInventory.items(this, getInventorySize(tier), IO.BOTH);
    }

    protected NotifiableInventory<AEFluidKey> createTank(Object... args) {
        return NotifiableInventory.fluids(this, getTankSize(tier), FluidHatchPartMachine.getTankCapacity(
                DualHatchPartMachine.INITIAL_TANK_CAPACITY, tier), IO.BOTH);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            TaskHandler.enqueueTask(serverLevel, this::updateAutoOutputSubscription, 0);
            this.invSubs = inventory.addChangedListener(this::updateAutoOutputSubscription);
            this.tankSubs = tank.addChangedListener(this::updateAutoOutputSubscription);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (invSubs != null) {
            invSubs.unsubscribe();
            this.invSubs = null;
        }
        if (tankSubs != null) {
            tankSubs.unsubscribe();
            this.tankSubs = null;
        }
    }

    ////////////////////////////////
    // ******* Auto Output *******//
    ////////////////////////////////
    @Override
    public void setAutoOutputFluids(boolean allow) {
        this.autoOutputFluids = allow;
        updateAutoOutputSubscription();
    }

    @Override
    public void setOutputFacingFluids(@Nullable Direction outputFacing) {
        clearDirectionCache();
        this.outputFacingFluids = outputFacing;
        updateAutoOutputSubscription();
    }

    @Override
    public void setAutoOutputItems(boolean allow) {
        this.autoOutputItems = allow;
        updateAutoOutputSubscription();
    }

    @Override
    public void setOutputFacingItems(@Nullable Direction outputFacing) {
        clearDirectionCache();
        this.outputFacingItems = outputFacing;
        updateAutoOutputSubscription();
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoOutputSubscription();
    }

    protected void updateAutoOutputSubscription() {
        var outputFacingItems = getOutputFacingItems();
        var outputFacingFluids = getOutputFacingFluids();
        if ((isAutoOutputItems() && !inventory.isEmpty() && outputFacingItems != null && holder.blockEntityDirectionCache.hasAdjacentTarget(getLevel(), getPos(), outputFacingItems, AEKeyTypes.ITEMS, StorageAccess.INSERT)) || (isAutoOutputFluids() && !tank.isEmpty() && outputFacingFluids != null && holder.blockEntityDirectionCache.hasAdjacentTarget(getLevel(), getPos(), outputFacingFluids, AEKeyTypes.FLUIDS, StorageAccess.INSERT))) {
            autoOutputSubs = subscribeServerTick(autoOutputSubs, autoOutputMonitor, 20);
        } else if (autoOutputSubs != null) {
            autoOutputSubs.unsubscribe();
            autoOutputSubs = null;
        }
    }

    protected void autoOutput() {
        if (isAutoOutputFluids() && getOutputFacingFluids() != null) {
            tank.exportToNearby(getOutputFacingFluids());
        }
        if (isAutoOutputItems() && getOutputFacingItems() != null) {
            inventory.exportToNearby(getOutputFacingItems());
        }
        updateAutoOutputSubscription();
    }

    ////////////////////////////////
    // ********** GUI *********** //
    ////////////////////////////////
    @Override
    public Widget createUIWidget() {
        int invTier = getTankSize(tier);
        var group = new WidgetGroup(0, 0, 18 * (invTier + 1) + 16, 18 * invTier + 16);
        var container = new WidgetGroup(4, 4, 18 * (invTier + 1) + 8, 18 * invTier + 8);
        var items = new MenuItemAdapter(getInventory().storage);
        var fluids = new ForgeFluidAdapter(tank.storage);
        int index = 0;
        for (int y = 0; y < invTier; y++) {
            for (int x = 0; x < invTier; x++) {
                container.addWidget(new SlotWidget(items, index++, 4 + x * 18, 4 + y * 18, true, true).setBackgroundTexture(GuiTextures.SLOT));
            }
        }
        index = 0;
        for (int y = 0; y < invTier; y++) {
            container.addWidget(new TankWidget(fluids, index++, 4 + invTier * 18, 4 + y * 18, true, true).setBackground(GuiTextures.FLUID_SLOT));
        }
        container.setBackground(GuiTextures.BACKGROUND_INVERSE);
        group.addWidget(container);
        return group;
    }

    ///////////////////////////////
    // ******* Rendering ********//
    ///////////////////////////////
    @Override
    public ResourceTexture sideTips(Player player, BlockPos pos, BlockState state, Set<GTToolType> toolTypes, Direction side) {
        if (toolTypes.contains(GTToolType.SCREWDRIVER)) {
            if (side == getOutputFacingItems() || side == getOutputFacingFluids()) {
                return GuiTextures.TOOL_ALLOW_INPUT;
            }
        }
        return super.sideTips(player, pos, state, toolTypes, side);
    }

    ////////////////////////////////
    // ********** Misc ***********//
    ////////////////////////////////
    @Override
    public void onMachineRemoved() {
        clearInventory(inventory.storage);
    }

    public void setAllowInputFromOutputSideItems(final boolean allowInputFromOutputSideItems) {
        clearDirectionCache();
        this.allowInputFromOutputSideItems = allowInputFromOutputSideItems;
    }

    public void setAllowInputFromOutputSideFluids(final boolean allowInputFromOutputSideFluids) {
        clearDirectionCache();
        this.allowInputFromOutputSideFluids = allowInputFromOutputSideFluids;
    }
}
