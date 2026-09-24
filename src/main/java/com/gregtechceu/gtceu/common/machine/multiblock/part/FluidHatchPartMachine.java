package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.CircuitFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDistinctPart;
import com.gregtechceu.gtceu.api.machine.multiblock.part.WorkableTieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.CircuitHandler;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.handler.IFilteredHandler;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.uiwidgets.inventory.HatchViews;
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidType;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FluidHatchPartMachine extends WorkableTieredIOPartMachine implements IMachineLife, IDistinctPart {

    public static final int INITIAL_TANK_CAPACITY_1X = 8 * FluidType.BUCKET_VOLUME;
    public static final int INITIAL_TANK_CAPACITY_4X = 2 * FluidType.BUCKET_VOLUME;
    public static final int INITIAL_TANK_CAPACITY_9X = FluidType.BUCKET_VOLUME;
    @SaveToDisk
    public final NotifiableFluidTank tank;
    protected final int slots;
    @Nullable
    protected TickableSubscription autoIOSubs;
    @Nullable
    protected ISubscription tankSubs;
    @Getter
    @SaveToDisk
    protected final NotifiableItemStackHandler circuitInventory;
    @Getter
    @SaveToDisk(defaultValue = "false")
    @SyncToClient
    protected boolean isDistinct = false;

    @Getter
    @SaveToDisk(defaultValue = "0")
    protected int priority;

    // The `Object... args` parameter is necessary in case a superclass needs to pass any args along to createTank().
    // We can't use fields here because those won't be available while createTank() is called.
    public FluidHatchPartMachine(MetaMachineBlockEntity holder, int tier, IO io, int initialCapacity, int slots, Object... args) {
        super(holder, tier, io);
        this.slots = slots;
        this.tank = createTank(initialCapacity, slots, args);
        this.circuitInventory = createCircuitItemHandler(io);
        if (io == IO.IN) this.workingEnabled = false;
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    protected NotifiableFluidTank createTank(int initialCapacity, int slots, Object... args) {
        return new NotifiableFluidTank(this, slots, getTankCapacity(initialCapacity, getTier()), io);
    }

    public static int getTankCapacity(int initialCapacity, int tier) {
        return initialCapacity * (1 << tier);
    }

    protected NotifiableItemStackHandler createCircuitItemHandler(Object... args) {
        if (args.length > 0 && args[0] instanceof IO io && io == IO.IN) {
            return CircuitHandler.create(this);
        } else {
            return NotifiableItemStackHandler.empty(this);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            TaskHandler.enqueueTask(serverLevel, this::updateTankSubscription, 0);
            getHandlerUnit().setDistinct(isDistinct);
            getHandlerUnit().setColor(getPaintingColor());
            tankSubs = tank.addChangedListener(this::updateTankSubscription);
            tank.setPriority(priority);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (tankSubs != null) {
            tankSubs.unsubscribe();
            tankSubs = null;
        }
    }

    @Override
    public void setDistinct(boolean distinct) {
        isDistinct = (io != IO.OUT && distinct);
        getHandlerUnit().setDistinctAndNotify(isDistinct);
    }

    protected void setPriority(int priority) {
        if (priority == Integer.MIN_VALUE) return;
        this.priority = priority;
        tank.setPriority(priority);
        RecipeHandlerUnit.notify(this);
    }

    @Override
    public void onPaintingColorChanged(int color) {
        getHandlerUnit().setColor(color, true);
    }

    @Override
    public int tintColor(int index) {
        if (index == 9) return getRealColor();
        return -1;
    }

    //////////////////////////////////////
    // ******** Auto IO *********//
    //////////////////////////////////////
    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateTankSubscription();
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        updateTankSubscription(newFacing);
    }

    protected void updateTankSubscription() {
        updateTankSubscription(getFrontFacing());
    }

    protected void updateTankSubscription(Direction newFacing) {
        if (isWorkingEnabled() && ((io == IO.OUT && !tank.isEmpty()) || io == IO.IN) && holder.blockEntityDirectionCache.hasAdjacentFluidHandler(getLevel(), getPos(), newFacing)) {
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoIO, 20);
        } else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    protected void autoIO() {
        if (isWorkingEnabled()) {
            if (io == IO.OUT) {
                tank.exportToNearby(getFrontFacing());
            } else if (io == IO.IN) {
                tank.importFromNearby(getFrontFacing());
            }
        }
        updateTankSubscription();
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        super.setWorkingEnabled(workingEnabled);
        updateTankSubscription();
    }

    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        InteractionResult superResult = super.onScrewdriverClick(playerIn, hand, gridSide, hitResult);
        if (superResult != InteractionResult.PASS) return superResult;
        if (io == IO.BOTH) return InteractionResult.PASS;
        if (playerIn.isShiftKeyDown()) {
            if (swapIO()) {
                return InteractionResult.sidedSuccess(playerIn.level().isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    public boolean swapIO() {
        BlockPos blockPos = getHolder().pos();
        MachineDefinition newDefinition = null;
        if (io == IO.IN) {
            if (this.slots == 1) newDefinition = GTMachines.FLUID_EXPORT_HATCH[this.getTier()];
            else if (this.slots == 4) newDefinition = GTMachines.FLUID_EXPORT_HATCH_4X[this.getTier()];
            else if (this.slots == 9) newDefinition = GTMachines.FLUID_EXPORT_HATCH_9X[this.getTier()];
        } else if (io == IO.OUT) {
            if (this.slots == 1) newDefinition = GTMachines.FLUID_IMPORT_HATCH[this.getTier()];
            else if (this.slots == 4) newDefinition = GTMachines.FLUID_IMPORT_HATCH_4X[this.getTier()];
            else if (this.slots == 9) newDefinition = GTMachines.FLUID_IMPORT_HATCH_9X[this.getTier()];
        }
        if (newDefinition == null) return false;
        BlockState newBlockState = newDefinition.get().defaultBlockState();
        getLevel().setBlockAndUpdate(blockPos, newBlockState);
        if (getLevel().getBlockEntity(blockPos) instanceof MetaMachineBlockEntity newHolder) {
            if (newHolder.getMetaMachine() instanceof FluidHatchPartMachine newMachine) {
                newMachine.setFrontFacing(this.getFrontFacing());
                newMachine.setUpwardsFacing(this.getUpwardsFacing());
                newMachine.setPaintingColor(this.getPaintingColor());
                for (int i = 0; i < this.tank.getTanks(); i++) {
                    newMachine.tank.setFluidInTank(i, this.tank.getFluidInTank(i));
                }
            }
        }
        return true;
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////
    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        if (this.io == IO.OUT) {
            IDistinctPart.super.superAttachConfigurators(configuratorPanel);
        } else if (this.io == IO.IN) {
            IDistinctPart.super.attachConfigurators(configuratorPanel);
            configuratorPanel.attachConfigurators(new CircuitFancyConfigurator(circuitInventory.storage));
        }
    }

    @Override
    public void attachSideTabs(TabsWidget sideTabs) {
        super.attachSideTabs(sideTabs);
        sideTabs.attachSubTab(IFilteredHandler.createPriorityConfigurator(this::getPriority, this::setPriority));
    }

    @Override
    public Widget createUIWidget() {
        return slots == 1 ? HatchViews.singleTank(tank, io) : HatchViews.page(HatchViews.tanks(tank.getStorages(), io));
    }
}
