package com.gregtechceu.gtceu.api.item.tool;

import com.gregtechceu.gtceu.api.item.IDataComponentItem;
import com.gregtechceu.gtceu.api.item.IGTTool;
import com.gregtechceu.gtceu.api.item.ItemDataComponentKey;
import com.gregtechceu.gtceu.api.tooltip.Tips;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uiwidgets.item.HeldItemPage;
import com.gregtechceu.gtceu.utils.input.KeyBind;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 工具的一项可调设置：J 键调当前项、Shift+J 换项、潜行右键空气打开的设置界面和提示框都从这里读。
 */
public abstract class ToolSetting {

    public static final ToolSetting[] NONE = new ToolSetting[0];
    public static final ItemDataComponentKey<Byte, CompoundTag> SELECTED = ToolData.byteField(ToolData.BEHAVIOURS_TAG_KEY, "Setting");
    private static final Component SETTING_SELECTED = Component.translatable("item.gtceu.tool.setting.selected");
    private static final Component SETTING_SELECTED_DETAIL = Component.translatable("item.gtceu.tool.setting.selected.detail");

    public final String nameKey;
    public final String detailKey;

    protected ToolSetting(String nameKey, String detailKey) {
        this.nameKey = nameKey;
        this.detailKey = detailKey;
    }

    public abstract Component value(ItemStack stack);

    public abstract void cycle(ItemStack stack);

    public abstract void addRows(UIElement page, HeldItemUIFactory.HeldItemHolder holder);

    public static ToolSetting[] of(ItemStack stack) {
        return stack.getItem() instanceof IGTTool tool ? tool.getToolStats().settings : NONE;
    }

    public static int selected(ItemStack stack, ToolSetting[] settings) {
        int index = IDataComponentItem.getOrDefault(stack, SELECTED, (byte) 0);
        return index >= 0 && index < settings.length ? index : 0;
    }

    private static void select(ItemStack stack, int index) {
        IDataComponentItem.set(stack, SELECTED, index == 0 ? null : (byte) index);
    }

    public static void onKey(ServerPlayer player, boolean switchSetting) {
        if (player.isSpectator() || !player.isAlive()) return;
        ItemStack stack = player.getMainHandItem();
        ToolSetting[] settings = of(stack);
        if (settings.length == 0) {
            stack = player.getOffhandItem();
            settings = of(stack);
            if (settings.length == 0) return;
        }
        int index = selected(stack, settings);
        if (switchSetting) {
            index = (index + 1) % settings.length;
            select(stack, index);
        } else {
            settings[index].cycle(stack);
        }
        ToolSetting setting = settings[index];
        player.displayClientMessage(Component.translatable("item.gtceu.tool.setting.value",
                Component.translatable(setting.nameKey), setting.value(stack)), true);
    }

    public static boolean canOpenUI(Player player, InteractionHand hand) {
        return player.isShiftKeyDown() && of(player.getItemInHand(hand)).length > 0;
    }

    public static ModularUI createUI(Player player, HeldItemUIFactory.HeldItemHolder holder) {
        ToolSetting[] settings = of(holder.getHeld());
        return new HeldItemPage(holder, window -> {
            UIElement page = Form.page();
            if (settings.length > 1) {
                page.addChild(Form.choiceRow(SETTING_SELECTED, settings.length,
                        i -> Component.translatable(settings[i].nameKey),
                        () -> selected(holder.getHeld(), settings),
                        i -> {
                            ItemStack held = heldTool(holder);
                            if (!held.isEmpty()) select(held, i);
                        },
                        SETTING_SELECTED_DETAIL));
            }
            for (ToolSetting setting : settings) {
                setting.addRows(page, holder);
            }
            return page;
        }).noInventory().createUI(player);
    }

    public static ItemStack heldTool(HeldItemUIFactory.HeldItemHolder holder) {
        ItemStack held = holder.getHeld();
        return held.getItem() instanceof IGTTool ? held : ItemStack.EMPTY;
    }

    public static void addControls(ItemStack stack, ToolSetting[] settings, List<Component> tooltip) {
        if (settings.length == 0) return;
        ToolSetting current = settings[selected(stack, settings)];
        tooltip.add(Tips.hotkey(KeyBind.TOOL_SETTING, Component.translatable("gtceu.tip.toggle",
                Component.translatable("item.gtceu.tool.setting.adjust", Component.translatable(current.nameKey)), current.value(stack))));
        if (settings.length > 1) {
            tooltip.add(Tips.control(Tips.keyAction(Component.translatable("item.gtceu.tool.setting.shift_key",
                    Tips.keyName(KeyBind.TOOL_SETTING)), Component.translatable("item.gtceu.tool.setting.switch"))));
        }
        tooltip.add(Tips.rightClick(Tips.keyAction(Component.translatable("item.gtceu.tool.setting.sneak_air"),
                Component.translatable("item.gtceu.tool.setting.open"))));
    }

    public static void addSettings(ItemStack stack, ToolSetting[] settings, List<Component> tooltip, boolean details) {
        if (settings.length == 0) return;
        tooltip.add(Tips.heading(Component.translatable("item.gtceu.tool.tooltip.settings")));
        for (ToolSetting setting : settings) {
            tooltip.add(Tips.stat(Component.translatable("item.gtceu.tool.setting.value",
                    Component.translatable(setting.nameKey), setting.value(stack))));
            if (details) tooltip.add(Tips.detail(Component.translatable(setting.detailKey)));
        }
    }

    public static class Choice extends ToolSetting {

        private final Component[] options;
        private final Getter getter;
        private final Setter setter;

        public Choice(String nameKey, String detailKey, Component[] options, Getter getter, Setter setter) {
            super(nameKey, detailKey);
            this.options = options;
            this.getter = getter;
            this.setter = setter;
        }

        protected Component[] options(ItemStack stack) {
            return options;
        }

        private int index(ItemStack stack, int count) {
            int index = getter.get(stack);
            return index >= 0 && index < count ? index : 0;
        }

        @Override
        public Component value(ItemStack stack) {
            Component[] options = options(stack);
            return options.length == 0 ? Component.empty() : options[index(stack, options.length)];
        }

        @Override
        public void cycle(ItemStack stack) {
            int count = options(stack).length;
            if (count > 0) setter.set(stack, (index(stack, count) + 1) % count);
        }

        @Override
        public void addRows(UIElement page, HeldItemUIFactory.HeldItemHolder holder) {
            Component[] options = options(holder.getHeld());
            page.addChild(Form.choiceRow(Component.translatable(nameKey), options.length, i -> options[i],
                    () -> index(holder.getHeld(), options.length),
                    i -> {
                        ItemStack held = heldTool(holder);
                        if (!held.isEmpty()) setter.set(held, i);
                    }, Component.translatable(detailKey)));
        }
    }

    @FunctionalInterface
    public interface Getter {

        int get(ItemStack stack);
    }

    @FunctionalInterface
    public interface Setter {

        void set(ItemStack stack, int index);
    }
}
