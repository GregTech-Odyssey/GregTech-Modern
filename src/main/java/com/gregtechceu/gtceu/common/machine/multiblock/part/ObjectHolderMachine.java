package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IObjectHolder;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.component.IDataItem;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.ICapabilityTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableContentHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.handler.PlanScratch;
import com.gregtechceu.gtceu.api.recipe.ui.RecipeSlotLayouts;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.StackInventory;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
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
public class ObjectHolderMachine extends MultiblockPartMachine implements IObjectHolder, IMachineLife {

    // purposefully not exposed to automation or capabilities
    @SaveToDisk
    private final ObjectHolderHandler heldItems;
    @Getter
    @Setter
    @SaveToDisk
    @SyncToClient
    private boolean isLocked;

    public ObjectHolderMachine(MetaMachineBlockEntity holder) {
        super(holder);
        heldItems = new ObjectHolderHandler(this);
        heldItems.addChangedListener(() -> getControllers().forEach(controller -> {
            if (controller instanceof IRecipeLogicMachine rlm)
                rlm.getRecipeLogic().updateTickSubscription();
        }));
    }

    @Override
    public ItemStack getHeldItem(boolean remove) {
        return getHeldItem(0, remove);
    }

    @Override
    public void setHeldItem(ItemStack heldItem) {
        heldItems.storage.setStackInSlot(0, heldItem);
    }

    @Override
    public ItemStack getDataItem(boolean remove) {
        return getHeldItem(1, remove);
    }

    @Override
    public void setDataItem(ItemStack dataItem) {
        heldItems.storage.setStackInSlot(1, dataItem);
    }

    @Override
    public ObjectHolderHandler getAsHandler() {
        return heldItems;
    }

    private ItemStack getHeldItem(int slot, boolean remove) {
        ItemStack stackInSlot = heldItems.storage.getStackInSlot(slot);
        if (remove && stackInSlot != ItemStack.EMPTY) {
            heldItems.storage.setStackInSlot(slot, ItemStack.EMPTY);
        }
        return stackInSlot;
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(this.heldItems.storage);
    }

    @Override
    public Widget createUIWidget() {
        var canvas = RecipeSlotLayouts.canvas(115, 60);
        RecipeSlotLayouts.place(canvas, heldSlot(1, GuiTextures.DATA_ORB_OVERLAY), 0, 21);
        RecipeSlotLayouts.image(canvas, GuiTextures.PROGRESS_BAR_RESEARCH_STATION_BASE, 31, 0, 84, 60);
        RecipeSlotLayouts.place(canvas, heldSlot(0, GuiTextures.RESEARCH_STATION_OVERLAY), 64, 21);
        return canvas;
    }

    private ItemSlot heldSlot(int index, IGuiTexture overlay) {
        var slot = ItemSlot.of(heldItems.storage, index).disabled(this::isLocked, null);
        slot.setBackgroundTexture(new GuiTextureGroup(UITheme.ITEM_SLOT, overlay));
        return slot;
    }

    @Override
    public void setFrontFacing(Direction frontFacing) {
        super.setFrontFacing(frontFacing);
        var controllers = getControllers();
        for (var controller : controllers) {
            if (controller != null && controller.isFormed()) {
                controller.requestCheck();
            }
        }
    }

    private static long reserved(PlanScratch plan, int member, int slot, boolean consumeOnly) {
        long r = 0;
        int n = plan.logSize();
        for (int i = 0; i < n; i++) {
            if (plan.logMember(i) == member && plan.logToken(i) == slot && !plan.logIsFluid(i) && (!consumeOnly || plan.logConsumes(i))) r += plan.logAmount(i);
        }
        return r;
    }

    private static final class HeldStacks extends StackInventory {

        private HeldStacks() {
            super(2);
        }

        // only allow a single item, no stack size
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return 1;
        }

        // only allow data items in the second slot
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty()) {
                return true;
            }
            boolean isDataItem = false;
            if (stack.getItem() instanceof IComponentItem metaItem) {
                for (IItemComponent behaviour : metaItem.getComponents()) {
                    if (behaviour instanceof IDataItem) {
                        isDataItem = true;
                        break;
                    }
                }
            }
            if (slot == 0 && !isDataItem) {
                return true;
            } else return slot == 1 && isDataItem;
        }
    }

    public class ObjectHolderHandler extends NotifiableContentHandler implements IRecipeHandler, ICapabilityTrait, IKeyHandler<AEItemKey> {

        @SaveToDisk
        public final StackInventory storage;
        private final long[] take;
        private final ItemStack[] undo;

        public ObjectHolderHandler(MetaMachine metaTileEntity) {
            super(metaTileEntity, IO.IN);
            this.storage = new HeldStacks();
            this.take = new long[storage.size];
            this.undo = new ItemStack[storage.size];
            storage.setOnContentsChanged(this::onContentsChanged);
        }

        @Override
        public IO getCapabilityIO() {
            return IO.BOTH;
        }

        @Override
        public boolean canCapOutput() {
            return !isLocked() && ICapabilityTrait.super.canCapOutput();
        }

        @Override
        public boolean handlesItems() {
            return true;
        }

        @Override
        public long available(AEKeyType type, KeyIngredient ingredient) {
            long total = 0;
            for (var stack : storage.stacks) {
                if (!stack.isEmpty() && KeyIngredient.acceptsStack(ingredient, stack)) total += stack.getCount();
            }
            return total;
        }

        @Override
        public long reserveInput(PlanScratch plan, int member, AEKeyType type, int entry, KeyIngredient ingredient, long need, boolean consume) {
            long got = 0;
            for (int s = 0; s < storage.size && got < need; s++) {
                var stack = storage.stacks[s];
                if (stack.isEmpty() || !KeyIngredient.acceptsStack(ingredient, stack)) continue;
                long free = stack.getCount() - reserved(plan, member, s, false);
                if (free <= 0) continue;
                long t = Math.min(free, need - got);
                plan.logCustom(member, s, entry, t, type, consume, false);
                got += t;
            }
            return got;
        }

        @Override
        public boolean commitInput(PlanScratch plan, int member, AEKeyType type) {
            var stacks = storage.stacks;
            for (int s = 0; s < storage.size; s++) {
                take[s] = reserved(plan, member, s, true);
                if (take[s] > stacks[s].getCount()) return false;
            }
            for (int s = 0; s < storage.size; s++) {
                undo[s] = take[s] > 0 ? stacks[s].copy() : null;
            }
            for (int s = 0; s < storage.size; s++) {
                if (take[s] > 0) storage.extract(s, stacks[s], (int) take[s], false);
            }
            return true;
        }

        @Override
        public void rollbackInput(PlanScratch plan, int member, AEKeyType type) {
            for (int s = 0; s < storage.size; s++) {
                if (undo[s] != null) {
                    storage.setStackInSlot(s, undo[s]);
                    undo[s] = null;
                }
            }
        }

        @Override
        public boolean forEachKey(AEKeyType type, KeyVisitor visitor) {
            if (type != AEKeyTypes.ITEMS) return false;
            for (var stack : storage.stacks) {
                if (!stack.isEmpty() && visitor.visit(AEItemKey.of(stack), stack.getCount())) return true;
            }
            return false;
        }

        @Override
        public void fillSearchMap(@NotNull GTRecipeType type, @NotNull IntLongMap map) {
            for (var stack : storage.stacks) {
                if (!stack.isEmpty()) type.convertKey(AEItemKey.of(stack), stack.getCount(), map);
            }
        }

        @Override
        public boolean updateEmpty() {
            return storage.isEmpty();
        }

        @Override
        public AEKeyType keyType() {
            return AEKeyTypes.ITEMS;
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
