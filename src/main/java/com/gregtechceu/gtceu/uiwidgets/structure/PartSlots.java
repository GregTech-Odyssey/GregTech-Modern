package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.integration.xei.handlers.item.CycleItemStackHandler;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.util.TextFormattingUtil;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class PartSlots {

    private PartSlots() {}

    public static List<SlotWidget> create(List<ItemStack> stacks) {
        return slots(stacks, Collections.emptyList());
    }

    public static List<SlotWidget> create(List<ItemStack> stacks, @Nullable Component hint) {
        return slots(stacks, hint == null ? Collections.emptyList() : Collections.singletonList(hint));
    }

    public static WidgetGroup grid(List<ItemStack> stacks, List<Component> tips, int columns) {
        var slots = slots(stacks, tips);
        int rows = Math.max(1, (slots.size() + columns - 1) / columns);
        var grid = new WidgetGroup(0, 0, columns * UISizes.SLOT, rows * UISizes.SLOT);
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            slot.setSelfPosition(new Position((i % columns) * UISizes.SLOT, (i / columns) * UISizes.SLOT));
            grid.addWidget(slot);
        }
        return grid;
    }

    private static List<SlotWidget> slots(List<ItemStack> stacks, List<Component> tips) {
        var lists = new ArrayList<List<ItemStack>>(stacks.size());
        for (var stack : stacks) lists.add(Collections.singletonList(stack.copyWithCount(1)));
        var handler = new CycleItemStackHandler(lists);
        var slots = new ArrayList<SlotWidget>(stacks.size());
        for (int i = 0; i < stacks.size(); i++) {
            var slot = new SlotWidget(handler, i, 0, 0, false, false);
            slot.setBackgroundTexture(ColorPattern.T_GRAY.rectTexture());
            slot.setIngredientIO(IngredientIO.INPUT);
            int count = stacks.get(i).getCount();
            var exact = count > 1 ? Component.literal(String.format("×%,d", count)) : null;
            if (count > 1) slot.setOverlay(countTexture(TextFormattingUtil.formatLongToCompactString(count, 4)));
            if (exact != null || !tips.isEmpty()) {
                slot.setOnAddedTooltips((widget, list) -> {
                    if (exact != null) list.add(exact);
                    list.addAll(tips);
                });
            }
            slots.add(slot);
        }
        return slots;
    }

    private static IGuiTexture countTexture(String text) {
        return (graphics, mouseX, mouseY, x, y, width, height) -> {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            DrawerHelper.drawStringFixedCorner(graphics, text, x + 17, y + 17, 0xFFFFFF, true, 0.5f);
            graphics.pose().popPose();
        };
    }
}
