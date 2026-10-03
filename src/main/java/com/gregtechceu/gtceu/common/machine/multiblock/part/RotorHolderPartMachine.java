package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.BlockableSlotWidget;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IRotorHolderMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.WorkableTieredPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.ICapabilityTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableContentHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandler;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.StackInventory;
import com.gregtechceu.gtceu.common.data.GTDamageTypes;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.TurbineRotorBehaviour;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import com.gto.recipesearch.IntLongMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class RotorHolderPartMachine extends WorkableTieredPartMachine implements IMachineLife, IRotorHolderMachine, IInteractedMachine {

    @SaveToDisk
    public final RotorInventory inventory;
    @Getter
    public final int maxRotorHolderSpeed;
    @Getter
    @SaveToDisk
    @SyncToClient
    public int rotorSpeed;
    @Setter
    @SaveToDisk
    @SyncToClient(scheduleUpdate = true)
    @NotNull
    public Material rotorMaterial = GTMaterials.NULL; // 0 - no rotor
    @Nullable
    protected TickableSubscription rotorSpeedSubs;
    @Nullable
    protected ISubscription rotorInvSubs;

    public RotorHolderPartMachine(MetaMachineBlockEntity holder, int tier) {
        super(holder, tier);
        this.inventory = new RotorInventory(this);
        this.inventory.storage.setFilter(i -> TurbineRotorBehaviour.getBehaviour(i) != null);
        this.maxRotorHolderSpeed = 2000 + 1000 * tier;
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public void onMachineRemoved() {
        clearInventory(inventory.storage);
    }

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            return getRotorMaterial().getMaterialARGB();
        }
        return super.tintColor(index);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            updateRotorSubscription();
            rotorInvSubs = this.inventory.addChangedListener(this::onRotorInventoryChanged);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (rotorInvSubs != null) {
            rotorInvSubs.unsubscribe();
        }
    }

    @Override
    public boolean canShared() {
        return false;
    }

    //////////////////////////////////////
    // ****** Rotor Holder ******//
    //////////////////////////////////////
    @Override
    public Material getRotorMaterial() {
        // handles clients trying to get the material before server data sync
        // noinspection ConstantValue
        if (rotorMaterial == null) {
            return GTMaterials.NULL;
        }
        return rotorMaterial;
    }

    private void onRotorInventoryChanged() {
        var stack = getRotorStack();
        var rotorBehaviour = TurbineRotorBehaviour.getBehaviour(stack);
        if (rotorBehaviour != null) {
            this.rotorMaterial = rotorBehaviour.getPartMaterial(stack);
        } else {
            this.rotorMaterial = GTMaterials.NULL;
        }
        for (var controller : getControllers()) {
            if (controller instanceof IRecipeLogicMachine recipeLogicMachine) {
                recipeLogicMachine.getRecipeLogic().updateTickSubscription();
            }
        }
    }

    @Override
    public boolean hasRotor() {
        return inventory.storage.getStackInSlot(0) != ItemStack.EMPTY;
    }

    protected void updateRotorSubscription() {
        if (rotorSpeed > 0) {
            rotorSpeedSubs = subscribeServerTick(rotorSpeedSubs, this::updateRotorSpeed);
        } else if (rotorSpeedSubs != null) {
            rotorSpeedSubs.unsubscribe();
            rotorSpeedSubs = null;
        }
    }

    private void updateRotorSpeed() {
        if (isFormed() && getController() instanceof IWorkableMultiController workable) {
            if (workable.getRecipeLogic().isWorking()) return;
        }
        if (!hasRotor()) {
            setRotorSpeed(0);
        } else if (rotorSpeed > 0) {
            setRotorSpeed(Math.max(0, rotorSpeed - SPEED_DECREMENT));
        }
        updateRotorSubscription();
    }

    public void setRotorSpeed(int rotorSpeed) {
        if ((this.rotorSpeed > 0 && rotorSpeed <= 0) || (this.rotorSpeed <= 0 && rotorSpeed > 0)) {
            scheduleRenderUpdate();
        }
        this.rotorSpeed = rotorSpeed;
    }

    @Override
    public void onWorking(IWorkableMultiController controller) {
        if (rotorSpeed < maxRotorHolderSpeed) {
            setRotorSpeed(rotorSpeed + SPEED_INCREMENT);
            updateRotorSubscription();
        }
        if (getOffsetTimer() % 20 == 0) {
            var numMaintenanceProblems = 0;
            if (isFormed() && getController() instanceof IMaintenanceMachine maintenance) {
                numMaintenanceProblems = maintenance.getNumMaintenanceProblems();
            }
            damageRotor(1 + numMaintenanceProblems);
        }
        super.onWorking(controller);
    }

    public int getTierDifference() {
        if (isFormed() && getController() instanceof ITieredMachine tieredMachine) {
            return getTier() - tieredMachine.getTier();
        }
        return -1;
    }

    @Override
    public ItemStack getRotorStack() {
        return inventory.storage.getStackInSlot(0);
    }

    @Override
    public void setRotorStack(ItemStack rotorStack) {
        inventory.storage.setStackInSlot(0, rotorStack);
        inventory.notifyListeners();
    }

    public InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isRemote() && rotorSpeed > 0 && !player.isCreative()) {
            player.hurt(GTDamageTypes.TURBINE.source(level), TurbineRotorBehaviour.getBehaviour(getRotorStack()).getDamage(getRotorStack()));
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////
    @Override
    public Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 18 + 16, 18 + 16);
        var container = new WidgetGroup(4, 4, 18 + 8, 18 + 8);
        container.addWidget(new BlockableSlotWidget(inventory.storage, 0, 4, 4).setIsBlocked(() -> rotorSpeed != 0).setBackground(GuiTextures.SLOT, GuiTextures.TURBINE_OVERLAY));
        container.setBackground(GuiTextures.BACKGROUND_INVERSE);
        group.addWidget(container);
        return group;
    }

    public static class RotorInventory extends NotifiableContentHandler implements IRecipeHandler, ICapabilityTrait, IKeyHandler<AEItemKey> {

        @SaveToDisk
        public final StackInventory storage;

        public RotorInventory(MetaMachine machine) {
            super(machine, IO.NONE);
            this.storage = new StackInventory(1);
            storage.setOnContentsChanged(this::onContentsChanged);
        }

        @Override
        public void fillSearchMap(@NotNull GTRecipeType type, @NotNull IntLongMap map) {}

        @Override
        public boolean updateEmpty() {
            return storage.isEmpty();
        }

        @Override
        public AEKeyType keyType() {
            return AEKeyType.items();
        }

        @Override
        public int size() {
            return storage.size;
        }

        @Override
        public @Nullable AEItemKey keyAt(int slot) {
            return storage.keyAt(slot);
        }

        @Override
        public long amountAt(int slot) {
            return storage.amountAt(slot);
        }

        @Override
        public long slotLimit(int slot) {
            return storage.slotLimit(slot);
        }

        @Override
        public long insert(int slot, AEItemKey key, long amount, boolean simulate) {
            return canCapInput() ? storage.insert(slot, key, amount, simulate) : 0;
        }

        @Override
        public long extract(int slot, AEItemKey key, long amount, boolean simulate) {
            return canCapOutput() ? storage.extract(slot, key, amount, simulate) : 0;
        }
    }
}
