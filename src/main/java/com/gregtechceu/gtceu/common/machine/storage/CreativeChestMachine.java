package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.PhantomSlotWidget;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.transfer.key.InfiniteKeySource;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.syncdata.annotation.DropSaved;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.items.ItemHandlerHelper;

import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.annotations.SaveToDisk;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@Getter
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CreativeChestMachine extends QuantumChestMachine {

    @SaveToDisk(defaultValue = "1")
    @DropSaved
    private int itemsPerCycle = 1;
    @SaveToDisk(defaultValue = "1")
    @DropSaved
    private int ticksPerCycle = 1;

    public CreativeChestMachine(MetaMachineBlockEntity holder) {
        super(holder, GTValues.MAX, -1);
    }

    @Override
    protected ItemCache createCacheItemHandler(Object... args) {
        return new InfiniteCache(this);
    }

    @Override
    protected void loadStored(@Nullable AEItemKey key, long amount) {
        cache.storage.set(0, key, key == null ? 0 : 1);
    }

    private InteractionResult updateStored(ItemStack item) {
        var key = Keys.item(item);
        cache.storage.set(0, key, key == null ? 0 : 1);
        return InteractionResult.SUCCESS;
    }

    private static int parseCycleValue(String value) {
        if (value.isEmpty()) return -1;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void setTicksPerCycle(String value) {
        int n = parseCycleValue(value);
        if (n < 1) return;
        ticksPerCycle = n;
        if (autoOutputSubs != null) autoOutputSubs.cycle = ticksPerCycle;
        onItemChanged();
    }

    private void setItemsPerCycle(String value) {
        int n = parseCycleValue(value);
        if (n < 1) return;
        itemsPerCycle = n;
        onItemChanged();
    }

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        var heldItem = player.getItemInHand(hand);
        if (hit.getDirection() == getFrontFacing() && !isRemote()) {
            var stored = getStored();
            // Clear item if empty hand + shift-rclick
            if (heldItem.isEmpty() && player.isCrouching() && !stored.isEmpty()) {
                return updateStored(ItemStack.EMPTY);
            }
            // If held item can stack with stored item, delete held item
            if (!heldItem.isEmpty() && ItemHandlerHelper.canItemStacksStack(stored, heldItem)) {
                player.setItemInHand(hand, ItemStack.EMPTY);
                return InteractionResult.SUCCESS;
            } else if (!heldItem.isEmpty()) {
                // If held item is different than stored item, update stored item
                return updateStored(heldItem);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 176, 131);
        group.addWidget(new PhantomSlotWidget(cache.storage, 0, 36, 6).setClearSlotOnRightClick(true).setMaxStackSize(1).setBackgroundTexture(GuiTextures.SLOT).setChangeListener(this::onChanged));
        group.addWidget(new LabelWidget(7, 9, "gtceu.creative.chest.item"));
        group.addWidget(new ImageWidget(7, 48, 154, 14, GuiTextures.DISPLAY));
        group.addWidget(new TextFieldWidget(9, 50, 152, 10, () -> String.valueOf(itemsPerCycle), this::setItemsPerCycle).setMaxStringLength(11).setNumbersOnly(1, Integer.MAX_VALUE));
        group.addWidget(new LabelWidget(7, 28, "gtceu.creative.chest.ipc"));
        group.addWidget(new ImageWidget(7, 85, 154, 14, GuiTextures.DISPLAY));
        group.addWidget(new TextFieldWidget(9, 87, 152, 10, () -> String.valueOf(ticksPerCycle), this::setTicksPerCycle).setMaxStringLength(11).setNumbersOnly(1, Integer.MAX_VALUE));
        group.addWidget(new LabelWidget(7, 65, "gtceu.creative.chest.tpc"));
        group.addWidget(new SwitchWidget(7, 101, 162, 20, (clickData, value) -> setWorkingEnabled(value)).setTexture(new GuiTextureGroup(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("gtceu.creative.activity.off")), new GuiTextureGroup(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("gtceu.creative.activity.on"))).setPressed(isWorkingEnabled()));
        return group;
    }

    private class InfiniteCache extends ItemCache {

        private final InfiniteKeySource<AEItemKey> source = new InfiniteKeySource<>(storage, false);

        public InfiniteCache(MetaMachine holder) {
            super(holder);
        }

        @Override
        public long slotLimit(int slot) {
            return source.slotLimit(slot);
        }

        @Override
        public long insert(int slot, AEItemKey key, long amount, boolean simulate) {
            if (slot != 0 || amount <= 0) return 0;
            return storage.amountAt(0) > 0 && storage.rawKeyAt(0) == key ? amount : 0;
        }

        @Override
        public long extract(int slot, AEItemKey key, long amount, boolean simulate) {
            return slot == 0 ? source.extract(0, key, amount, simulate) : 0;
        }

        @Override
        public long count(AEItemKey key) {
            return source.count(key);
        }

        @Override
        protected long exportLimit() {
            return itemsPerCycle;
        }
    }
}
