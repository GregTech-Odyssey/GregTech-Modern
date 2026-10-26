package com.gregtechceu.gtceu.api.item;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;

import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.ItemHandlerHelper;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Handheld super storage can receive matching items or drain Forge item/fluid containers. */
public class SuperStorageItem extends MetaMachineItem {

    private final long capacity;
    private final boolean tank;

    public SuperStorageItem(MetaMachineBlock block, Properties properties, long capacity) {
        this(block, properties, capacity, false);
    }

    protected SuperStorageItem(MetaMachineBlock block, Properties properties, long capacity, boolean tank) {
        super(block, properties);
        this.capacity = capacity;
        this.tank = tank;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack stack, @NotNull Slot slot, @NotNull ClickAction action, @NotNull Player player) {
        if (stack.getCount() != 1 || action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        var result = deposit(stack, slot.getItem(), player);
        if (result == null) return false;
        slot.setByPlayer(result);
        return true;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, @NotNull ItemStack other, @NotNull Slot slot, @NotNull ClickAction action, @NotNull Player player, @NotNull SlotAccess carried) {
        if (stack.getCount() != 1 || action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        var result = deposit(stack, other, player);
        if (result == null) return false;
        carried.set(result);
        slot.setChanged();
        return true;
    }

    private @Nullable ItemStack deposit(ItemStack stack, ItemStack source, Player player) {
        if (stack == source || stack.getTag() == null) return null;
        if (tank) {
            var destination = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
            if (destination == null) return null;
            boolean filled = false;
            for (int tank = 0; tank < destination.getTanks(); tank++) {
                if (!destination.getFluidInTank(tank).isEmpty()) {
                    filled = true;
                    break;
                }
            }
            if (!filled) return null;
            if (source.isEmpty()) return source;
            // Work on one container so stacked buckets cannot duplicate their contents.
            var from = FluidUtil.getFluidHandler(source.copyWithCount(1)).orElse(null);
            if (from == null) return source;
            boolean changed = false;
            for (int tank = 0; tank < from.getTanks(); tank++) {
                var fluid = from.getFluidInTank(tank);
                if (!fluid.isEmpty() && !FluidUtil.tryFluidTransfer(destination, from, fluid, true).isEmpty()) changed = true;
            }
            if (!changed) return source;
            var container = from.getContainer();
            if (source.getCount() == 1) return container;
            source.shrink(1);
            ItemHandlerHelper.giveItemToPlayer(player, container);
            return source;
        }
        var destination = getItemStorage(stack);
        if (destination.isEmpty()) return null;
        if (source.isEmpty()) return source;
        var from = source.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (from != null) {
            if (source.getCount() != 1) return source;
            for (int slot = 0; slot < from.getSlots(); slot++) {
                var preview = from.extractItem(slot, Integer.MAX_VALUE, true);
                var key = AEItemKey.of(preview);
                if (key == null) continue;
                int accepted = (int) destination.insert(key, preview.getCount(), true);
                if (accepted == 0) continue;
                var extracted = from.extractItem(slot, accepted, false);
                key = AEItemKey.of(extracted);
                if (key == null) continue;
                extracted.shrink((int) destination.insert(key, extracted.getCount(), false));
                if (!extracted.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, ItemHandlerHelper.insertItemStacked(from, extracted, false));
            }
        } else {
            source.shrink((int) destination.insert(AEItemKey.of(source), source.getCount(), false));
        }
        return source;
    }

    protected IKeyHandler<AEItemKey> getItemStorage(ItemStack stack) {
        return new ItemContents(stack, capacity);
    }

    private static final class ItemContents implements IKeyHandler<AEItemKey> {

        private final ItemStack stack;
        private final long capacity;
        private final KeyInventory<AEItemKey> contents;

        private ItemContents(ItemStack stack, long capacity) {
            this.stack = stack;
            this.capacity = capacity;
            contents = KeyInventory.items(1, capacity, false);
            var tag = stack.getTag();
            contents.set(0, AEItemKey.of(ItemStack.of(tag.getCompound("stored"))), tag.getLong("storedAmount"));
        }

        @Override
        public AEKeyType keyType() {
            return AEKeyTypes.ITEMS;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public AEItemKey keyAt(int slot) {
            return contents.keyAt(slot);
        }

        @Override
        public long amountAt(int slot) {
            return contents.amountAt(slot);
        }

        @Override
        public long slotLimit(int slot) {
            return capacity;
        }

        @Override
        public long insert(int slot, AEItemKey key, long amount, boolean simulate) {
            long inserted = contents.insert(slot, key, amount, simulate);
            if (inserted > 0 && !simulate) {
                var tag = stack.getOrCreateTag();
                tag.put("stored", key.toStack(1).serializeNBT());
                tag.putLong("storedAmount", contents.amountAt(0));
            }
            return inserted;
        }

        @Override
        public long extract(int slot, AEItemKey key, long amount, boolean simulate) {
            return 0;
        }
    }
}
