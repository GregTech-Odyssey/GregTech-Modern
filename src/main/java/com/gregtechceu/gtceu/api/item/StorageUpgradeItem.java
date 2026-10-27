package com.gregtechceu.gtceu.api.item;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.StorageUpgrades;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import lombok.Getter;

/** A capacity multiplier accepted by the four super-storage upgrade slots. */
@Getter
public final class StorageUpgradeItem extends Item {

    private final int capacityMultiplier;

    public StorageUpgradeItem(Properties properties, int capacityMultiplier) {
        super(properties);
        this.capacityMultiplier = capacityMultiplier;
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
        if (items.size() == 0) return InteractionResult.PASS;
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
