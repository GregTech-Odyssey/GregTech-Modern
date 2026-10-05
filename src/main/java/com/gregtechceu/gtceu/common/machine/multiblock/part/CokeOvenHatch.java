package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.InventoryProxyTrait;
import com.gregtechceu.gtceu.api.misc.TickTimeMonitor;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.common.data.GTTickTimeMonitors;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.CokeOvenMachine;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.StorageAccess;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CokeOvenHatch extends MultiblockPartMachine {

    public final InventoryProxyTrait<AEItemKey> inputInventory, outputInventory;
    public final InventoryProxyTrait<AEFluidKey> tank;
    @Nullable
    protected TickableSubscription autoIOSubs;
    protected final TickTimeMonitor autoIOMonitor = holder.monitorTick(GTTickTimeMonitors.AUTO_OUTPUT, this::autoIO);
    @Nullable
    protected ISubscription outputInventorySubs, outputTankSubs;

    public CokeOvenHatch(MetaMachineBlockEntity holder, Object... args) {
        super(holder);
        this.inputInventory = new InventoryProxyTrait<>(this, AEKeyTypes.ITEMS, IO.IN);
        this.outputInventory = new InventoryProxyTrait<>(this, AEKeyTypes.ITEMS, IO.OUT);
        this.tank = new InventoryProxyTrait<>(this, AEKeyTypes.FLUIDS, IO.BOTH);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public void onUnload() {
        super.onUnload();
        inputInventory.setProxy(null);
        outputInventory.setProxy(null);
        tank.setProxy(null);
        if (outputInventorySubs != null) {
            outputInventorySubs.unsubscribe();
            outputInventorySubs = null;
        }
        if (outputTankSubs != null) {
            outputTankSubs.unsubscribe();
            outputTankSubs = null;
        }
    }

    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        if (controller instanceof CokeOvenMachine cokeOven) {
            outputInventorySubs = cokeOven.exportItems.addChangedListener(this::updateAutoIOSubscription);
            outputTankSubs = cokeOven.exportFluids.addChangedListener(this::updateAutoIOSubscription);
            inputInventory.setProxy(cokeOven.importItems);
            outputInventory.setProxy(cokeOven.exportItems);
            tank.setProxy(cokeOven.exportFluids);
            notifyNeighborsUpdate();
            this.updateAutoIOSubscription();
        }
    }

    @Override
    public void removedFromController(IMultiController controller) {
        super.removedFromController(controller);
        inputInventory.setProxy(null);
        outputInventory.setProxy(null);
        tank.setProxy(null);
        if (outputInventorySubs != null) {
            outputInventorySubs.unsubscribe();
            outputInventorySubs = null;
        }
        if (outputTankSubs != null) {
            outputTankSubs.unsubscribe();
            outputTankSubs = null;
        }
    }

    @Override
    public boolean canShared() {
        return false;
    }

    @Override
    public boolean replacePartModelWhenFormed() {
        return false;
    }

    //////////////////////////////////////
    // ******** Auto IO *********//
    //////////////////////////////////////

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoIOSubscription();
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        updateAutoIOSubscription();
    }

    protected void updateAutoIOSubscription() {
        if ((!outputInventory.isEmpty() &&
                holder.blockEntityDirectionCache.hasAdjacentTarget(getLevel(), getPos(), getFrontFacing(), AEKeyTypes.ITEMS, StorageAccess.INSERT)) ||
                (!tank.isEmpty() && holder.blockEntityDirectionCache.hasAdjacentTarget(getLevel(), getPos(), getFrontFacing(), AEKeyTypes.FLUIDS, StorageAccess.INSERT))) {
            autoIOSubs = subscribeServerTick(autoIOSubs, autoIOMonitor, 20);
        } else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    protected void autoIO() {
        outputInventory.exportToNearby(getFrontFacing());
        tank.exportToNearby(getFrontFacing());
        updateAutoIOSubscription();
    }

    //////////////////////////////////////
    // ********* GUI *********//
    //////////////////////////////////////
    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }
}
