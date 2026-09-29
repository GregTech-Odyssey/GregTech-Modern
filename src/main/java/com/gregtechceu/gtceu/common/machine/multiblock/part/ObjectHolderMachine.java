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
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.ui.RecipeSlotLayouts;
import com.gregtechceu.gtceu.api.transfer.item.SingleCustomItemStackHandler;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import lombok.Setter;

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
        heldItems.setStackInSlot(0, heldItem);
    }

    @Override
    public ItemStack getDataItem(boolean remove) {
        return getHeldItem(1, remove);
    }

    @Override
    public void setDataItem(ItemStack dataItem) {
        heldItems.setStackInSlot(1, dataItem);
    }

    @Override
    public NotifiableItemStackHandler getAsHandler() {
        return heldItems;
    }

    private ItemStack getHeldItem(int slot, boolean remove) {
        ItemStack stackInSlot = heldItems.getStackInSlot(slot);
        if (remove && stackInSlot != ItemStack.EMPTY) {
            heldItems.setStackInSlot(slot, ItemStack.EMPTY);
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
        var slot = ItemSlot.of(heldItems, index).disabled(this::isLocked, null);
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

    public class ObjectHolderHandler extends NotifiableItemStackHandler {

        public ObjectHolderHandler(MetaMachine metaTileEntity) {
            super(metaTileEntity, 2, IO.IN, IO.BOTH, SingleCustomItemStackHandler::new);
        }

        // only allow a single item, no stack size
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean canCapOutput() {
            return !isLocked() && super.canCapOutput();
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
}
