package com.gregtechceu.gtceu.api.item.tool;

import com.gregtechceu.gtceu.api.item.IDataComponentItem;
import com.gregtechceu.gtceu.api.item.ItemDataComponentKey;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * GT 工具物品上的全部数据键；键名即存档格式，读写一律走数据组件以保证缓存一致。
 */
public final class ToolData {

    public static final String TOOL_TAG_KEY = "GT.Tool";
    public static final String BEHAVIOURS_TAG_KEY = "GT.Behaviours";

    public static final ItemDataComponentKey<Integer, CompoundTag> DAMAGE = intField(TOOL_TAG_KEY, ItemStack.TAG_DAMAGE);
    public static final ItemDataComponentKey<Integer, CompoundTag> TINT_COLOR = intField(TOOL_TAG_KEY, "TintColor");
    public static final ItemDataComponentKey<Boolean, Tag> UNBREAKABLE = flag("Unbreakable");
    public static final ItemDataComponentKey<Boolean, Tag> DISALLOW_CONTAINER_ITEM = flag("DisallowContainerItem");
    public static final ItemDataComponentKey<Integer, Tag> HIDE_FLAGS = ItemDataComponentKey.create("HideFlags", IntTag::valueOf, t -> t instanceof NumericTag n ? n.getAsInt() : null);

    private ToolData() {}

    public static int damage(ItemStack stack) {
        return IDataComponentItem.getInt(stack, DAMAGE);
    }

    public static void setDamage(ItemStack stack, int damage) {
        IDataComponentItem.set(stack, DAMAGE, damage);
    }

    public static boolean isUnbreakable(ItemStack stack) {
        return IDataComponentItem.getBoolean(stack, UNBREAKABLE);
    }

    static ItemDataComponentKey<Integer, CompoundTag> intField(String compound, String key) {
        return ItemDataComponentKey.createCompound(compound, (tag, value) -> tag.putInt(key, value),
                tag -> tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : null, tag -> tag.remove(key));
    }

    static ItemDataComponentKey<Byte, CompoundTag> byteField(String compound, String key) {
        return ItemDataComponentKey.createCompound(compound, (tag, value) -> tag.putByte(key, value),
                tag -> tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getByte(key) : null, tag -> tag.remove(key));
    }

    private static ItemDataComponentKey<Boolean, Tag> flag(String key) {
        return ItemDataComponentKey.create(key, ByteTag::valueOf, t -> t instanceof NumericTag n ? n.getAsByte() != 0 : null);
    }
}
