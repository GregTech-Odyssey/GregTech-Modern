package com.gregtechceu.gtceu.api.item.capability;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.item.IDataComponentItem;
import com.gregtechceu.gtceu.api.item.ItemDataComponentKey;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ElectricItem implements IElectricItem {

    public static final ItemDataComponentKey<Long, LongTag> CHARGE = ItemDataComponentKey.create(ToolHelper.CHARGE_KEY, ElectricItem::write, ElectricItem::read);
    public static final ItemDataComponentKey<Long, LongTag> MAX_CHARGE = ItemDataComponentKey.create(ToolHelper.MAX_CHARGE_KEY, ElectricItem::write, ElectricItem::read);
    public static final ItemDataComponentKey<Item, StringTag> BATTERY = ItemDataComponentKey.create(ToolHelper.BATTERY_KEY, ElectricItem::writeItem, ElectricItem::readItem);

    protected final ItemStack itemStack;

    protected final long maxCharge;
    protected final int tier;

    protected final boolean chargeable;
    protected final boolean canProvideEnergyExternally;
    protected final long transferAmps;

    public ElectricItem(ItemStack itemStack, long maxCharge, int tier, boolean chargeable,
                        boolean canProvideEnergyExternally) {
        this(itemStack, maxCharge, tier, chargeable, canProvideEnergyExternally, 1);
    }

    public ElectricItem(ItemStack itemStack, long maxCharge, int tier, boolean chargeable,
                        boolean canProvideEnergyExternally, long transferAmps) {
        this.itemStack = itemStack;
        this.maxCharge = maxCharge;
        this.tier = tier;
        this.chargeable = chargeable;
        this.canProvideEnergyExternally = canProvideEnergyExternally;
        this.transferAmps = transferAmps;
    }

    private static LongTag write(long change) {
        return LongTag.valueOf(change);
    }

    private static long read(LongTag tag) {
        return tag.getAsLong();
    }

    private static StringTag writeItem(Item item) {
        return StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    private static Item readItem(StringTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getAsString());
        return id != null && BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
    }

    public void setCharge(long change) {
        IDataComponentItem.set(itemStack, CHARGE, change);
    }

    public void setMaxChargeOverride(long maxCharge) {
        IDataComponentItem.set(itemStack, MAX_CHARGE, maxCharge);
    }

    @Override
    public long getTransferLimit() {
        return GTValues.V[getTier()] * transferAmps;
    }

    @Override
    public long getMaxCharge() {
        Long maxCharge = IDataComponentItem.get(itemStack, MAX_CHARGE);
        if (maxCharge == null) return this.maxCharge;
        return maxCharge;
    }

    public long getCharge() {
        var c = IDataComponentItem.getLong(itemStack, CHARGE);
        if (c == -666) return getMaxCharge();
        return Math.min(c, getMaxCharge());
    }

    public void setInfiniteCharge(boolean infiniteCharge) {
        IDataComponentItem.set(itemStack, CHARGE, infiniteCharge ? -666L : 0);
    }

    @Override
    public boolean canProvideChargeExternally() {
        return this.canProvideEnergyExternally;
    }

    @Override
    public boolean chargeable() {
        return chargeable;
    }

    @Override
    public long charge(long amount, int chargerTier, boolean ignoreTransferLimit, boolean simulate) {
        if (itemStack.getCount() != 1) {
            return 0L;
        }
        if ((chargeable || amount == Long.MAX_VALUE) && (chargerTier >= tier) && amount > 0L) {
            long canReceive = getMaxCharge() - getCharge();
            if (!ignoreTransferLimit) {
                amount = Math.min(amount, getTransferLimit());
            }
            long charged = Math.min(amount, canReceive);
            if (!simulate) {
                setCharge(getCharge() + charged);
            }
            return charged;
        }
        return 0;
    }

    @Override
    public long discharge(long amount, int chargerTier, boolean ignoreTransferLimit, boolean externally,
                          boolean simulate) {
        if (itemStack.getCount() != 1) {
            return 0L;
        }
        if ((canProvideEnergyExternally || !externally || amount == Long.MAX_VALUE) && (chargerTier >= tier) &&
                amount > 0L) {
            if (!ignoreTransferLimit) {
                amount = Math.min(amount, getTransferLimit());
            }
            long charge = getCharge();
            long discharged = Math.min(amount, charge);
            if (!simulate) {
                setCharge(charge - discharged);
            }
            return discharged;
        }
        return 0;
    }

    @Override
    public int getTier() {
        return tier;
    }
}
