package com.gregtechceu.gtceu.api.item;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.StorageUpgrades;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;

/** A capacity multiplier accepted by the four super-storage upgrade slots. */
@Getter
public final class StorageUpgradeItem extends Item {

    private static final Int2ObjectOpenHashMap<StorageUpgradeItem> BY_TIER = new Int2ObjectOpenHashMap<>(5);

    private final int capacityMultiplier;
    private final int tier;

    public StorageUpgradeItem(Properties properties, int capacityMultiplier) {
        super(properties);
        this.capacityMultiplier = capacityMultiplier;
        this.tier = Integer.numberOfTrailingZeros(capacityMultiplier);
        BY_TIER.put(tier, this);
    }

    public static StorageUpgradeItem byTier(int tier) {
        return BY_TIER.get(tier);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        var level = context.getLevel();
        var machine = MetaMachine.getMachine(level, context.getClickedPos());
        if (machine == null) return InteractionResult.PASS;
        var upgrades = machine.getTraits(StorageUpgrades.class);
        if (upgrades.length == 0) return InteractionResult.PASS;
        var items = upgrades[0].getItems();
        if (items.getSlots() == 0) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        for (int slot = 0; slot < items.size(); slot++) {
            int inserted = items.insert(slot, stack, 1, false);
            if (inserted > 0) {
                if (!player.isCreative()) stack.shrink(inserted);
                player.getInventory().setChanged();
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.FAIL;
    }
}
