package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.ButtonConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CrateMachine extends MetaMachine implements IFancyUIMachine, IMachineLife, IDropSaveMachine, IInteractedMachine {

    private static final int WIDE_INVENTORY = 90;
    private static final int PAGE_HEIGHT_LIMIT = 1024;

    @Getter
    private final Material material;
    @Getter
    private final int inventorySize;
    @Getter
    @SaveToDisk(defaultValue = "false")
    @SyncToClient(scheduleUpdate = true)
    private boolean isTaped;
    @SaveToDisk
    public final NotifiableItemStackHandler inventory;

    public CrateMachine(MetaMachineBlockEntity holder, Material material, int inventorySize) {
        super(holder);
        this.material = material;
        this.inventorySize = inventorySize;
        this.inventory = new NotifiableItemStackHandler(this, inventorySize, IO.BOTH);
    }

    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        if (widget instanceof MachineWindow window) window.setInventoryGutter(ScrollerView.SCROLL_BAR_SPACE);
        return createUIWidget();
    }

    @Override
    public Widget createUIWidget() {
        int columns = inventorySize >= WIDE_INVENTORY ? 2 * UISizes.SLOTS_PER_ROW : UISizes.SLOTS_PER_ROW;
        var grid = UIElement.column(columns * UISizes.SLOT);
        for (int start = 0; start < inventorySize; start += columns) {
            var row = UIElement.row(UISizes.SLOT);
            for (int i = start; i < Math.min(inventorySize, start + columns); i++) row.addChild(ItemSlot.of(inventory.storage, i));
            grid.addChild(row);
        }
        var scroller = new ScrollerView("crate.slots", columns * UISizes.SLOT, UISizes.SLOT).adaptiveWidth().setResizable(false);
        scroller.addScrollViewChild(grid);
        scroller.adaptiveHeight(isRemote() ? MachineWindow.clientPageHeightLimit(true) : PAGE_HEIGHT_LIMIT);
        return scroller;
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        IFancyUIMachine.super.attachConfigurators(configuratorPanel);
        configuratorPanel.attachConfigurators(new ButtonConfigurator(WidgetIcons.SORT, clickData -> {
            if (!clickData.isRemote) GTTransferUtils.sortInventory(inventory.storage);
        }).setTooltips(List.of(Component.translatable("gtceu.gui.inventory.sort"))));
    }

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isCrouching() && !isTaped) {
            if (stack.is(GTItems.DUCT_TAPE.asItem()) || stack.is(GTItems.BASIC_TAPE.asItem())) {
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
                isTaped = true;
                return InteractionResult.SUCCESS;
            }
        }
        return IInteractedMachine.super.onUse(state, world, pos, player, hand, hit);
    }

    @Override
    public void onMachinePlaced(@Nullable LivingEntity player, ItemStack stack) {
        IMachineLife.super.onMachinePlaced(player, stack);
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            this.isTaped = tag.getBoolean("taped");
            if (isTaped) {
                this.inventory.storage.deserializeNBT(tag.get("inventory"));
            }
            tag.remove("taped");
            this.isTaped = false;
        }
        stack.setTag(null);
    }

    @Override
    public void saveToItem(CompoundTag tag) {
        if (isTaped) {
            tag.putBoolean("taped", isTaped);
            tag.put("inventory", inventory.storage.serializeNBT());
        }
    }

    @Override
    public void loadFromItem(CompoundTag tag) {
        if (tag.getBoolean("taped")) isTaped = true;
        inventory.storage.deserializeNBT(tag.get("inventory"));
    }

    @Override
    public boolean saveBreak() {
        return isTaped;
    }

    @Override
    public void onMachineRemoved() {
        if (!isTaped) clearInventory(inventory.storage);
    }
}
