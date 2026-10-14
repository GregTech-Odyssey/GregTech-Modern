package com.gregtechceu.gtceu.common.item.tool.behavior;

import com.gregtechceu.gtceu.api.item.IDataComponentItem;
import com.gregtechceu.gtceu.api.item.ItemDataComponentKey;
import com.gregtechceu.gtceu.api.item.tool.ToolData;
import com.gregtechceu.gtceu.api.item.tool.ToolSetting;
import com.gregtechceu.gtceu.api.item.tool.behavior.IToolBehavior;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * 扳手的自动输出模式：右键机器侧面时，按模式把该面设为物品、流体或两者的自动输出面。
 */
public class ToolModeSwitchBehavior implements IToolBehavior {

    public static final ToolModeSwitchBehavior INSTANCE = new ToolModeSwitchBehavior();

    public static final ItemDataComponentKey<WrenchModeType, CompoundTag> MODE = ItemDataComponentKey.createCompound(
            ToolData.BEHAVIOURS_TAG_KEY, (tag, mode) -> tag.putByte("Mode", (byte) mode.ordinal()),
            tag -> tag.contains("Mode", Tag.TAG_ANY_NUMERIC) ? WrenchModeType.byId(tag.getByte("Mode")) : null,
            tag -> tag.remove("Mode"));

    private static final ToolSetting[] SETTINGS = { new ToolSetting.Choice("item.gtceu.tool.setting.wrench_mode",
            "item.gtceu.tool.setting.wrench_mode.detail",
            new Component[] { WrenchModeType.ITEM.name, WrenchModeType.FLUID.name, WrenchModeType.BOTH.name },
            stack -> getMode(stack).ordinal(), (stack, index) -> IDataComponentItem.set(stack, MODE, WrenchModeType.byId(index))) };

    protected ToolModeSwitchBehavior() {}

    public static WrenchModeType getMode(ItemStack stack) {
        WrenchModeType mode = IDataComponentItem.get(stack, MODE);
        return mode == null ? WrenchModeType.BOTH : mode;
    }

    @Override
    public void addBehaviorNBT(@NotNull ItemStack stack) {
        IDataComponentItem.set(stack, MODE, WrenchModeType.BOTH);
    }

    @Override
    public ToolSetting[] getSettings() {
        return SETTINGS;
    }

    @Getter
    public enum WrenchModeType {

        ITEM(Component.translatable("gtceu.mode.item")),
        FLUID(Component.translatable("gtceu.mode.fluid")),
        BOTH(Component.translatable("gtceu.mode.both"));

        private static final WrenchModeType[] VALUES = values();

        private final Component name;

        WrenchModeType(Component name) {
            this.name = name;
        }

        public static WrenchModeType byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : BOTH;
        }

        public boolean isItem() {
            return this == ITEM || this == BOTH;
        }

        public boolean isFluid() {
            return this == FLUID || this == BOTH;
        }
    }
}
