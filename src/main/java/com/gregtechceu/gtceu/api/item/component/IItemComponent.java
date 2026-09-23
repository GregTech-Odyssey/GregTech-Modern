package com.gregtechceu.gtceu.api.item.component;

import com.gregtechceu.gtceu.api.item.ComponentItem;

import net.minecraft.world.item.Item;

/**
 * Describes generic component attachable to {@link ComponentItem}
 * Multiple components can be attached to one item
 */
public interface IItemComponent {

    default void onAttached(Item item) {}

    /**
     * GTO: 返回 true 时，手持物品只因 NBT 变化（如电量）而改变时不播放重新装备动画
     */
    default boolean suppressReequipOnNbtChange() {
        return false;
    }
}
