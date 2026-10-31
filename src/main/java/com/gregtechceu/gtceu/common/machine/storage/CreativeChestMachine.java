package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.transfer.key.InfiniteKeySource;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.PhantomItemSlot;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@Getter
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CreativeChestMachine extends QuantumChestMachine {

    private static final Component ITEM_LABEL = Component.translatable("gtceu.creative.chest.item");
    private static final Component IPC_LABEL = Component.translatable("gtceu.creative.chest.ipc");
    private static final Component TPC_LABEL = Component.translatable("gtceu.creative.chest.tpc");
    private static final Component ACTIVITY_ON = Component.translatable("gtceu.creative.activity.on");
    private static final Component ACTIVITY_OFF = Component.translatable("gtceu.creative.activity.off");

    @SaveToDisk(defaultValue = "1")
    @SyncToClient
    private int itemsPerCycle = 1;
    @SaveToDisk(defaultValue = "1")
    @SyncToClient
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
        var key = AEItemKey.of(item);
        cache.storage.set(0, key, key == null ? 0 : 1);
        onItemChanged();
        return InteractionResult.SUCCESS;
    }

    private void setStored(ItemStack stack) {
        updateStored(stack);
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (forDrop) {
            tag.putInt("itemsPerCycle", itemsPerCycle);
            tag.putInt("ticksPerCycle", ticksPerCycle);
        }
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains("itemsPerCycle")) {
            itemsPerCycle = tag.getInt("itemsPerCycle");
        }
        if (tag.contains("ticksPerCycle")) {
            ticksPerCycle = tag.getInt("ticksPerCycle");
        }
    }

    public void setItemsPerCycle(int value) {
        if (value < 1) return;
        itemsPerCycle = value;
        onItemChanged();
    }

    public void setTicksPerCycle(int value) {
        if (value < 1) return;
        ticksPerCycle = value;
        if (autoOutputSubs != null) autoOutputSubs.cycle = ticksPerCycle;
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
        var slot = PhantomItemSlot.of(this::getStored, this::setStored, stack -> true).xeiPhantom();
        var page = Form.page();
        page.addChild(Form.controlRow(ITEM_LABEL, slot));
        page.addChild(Form.numberRow(IPC_LABEL,
                NumberField.ofInt(UISizes.CONTENT_WIDTH, this::getItemsPerCycle, this::setItemsPerCycle, 1, Integer.MAX_VALUE)));
        page.addChild(Form.numberRow(TPC_LABEL,
                NumberField.ofInt(UISizes.CONTENT_WIDTH, this::getTicksPerCycle, this::setTicksPerCycle, 1, Integer.MAX_VALUE)));
        page.addChild(Form.controlRow(() -> isWorkingEnabled() ? ACTIVITY_ON : ACTIVITY_OFF, Switch.of(this::isWorkingEnabled, this::setWorkingEnabled)));
        return page;
    }

    private class InfiniteCache extends ItemCache {

        private final InfiniteKeySource<AEItemKey> source = new InfiniteKeySource<>(storage, false);

        public InfiniteCache(MetaMachine holder) {
            super(holder);
        }

        @Override
        public long slotLimit(int slot) {
            return storage.keyAt(0) != null ? itemsPerCycle : 0;
        }

        @Override
        public long amountAt(int slot) {
            return storage.keyAt(0) != null ? itemsPerCycle : 0;
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
