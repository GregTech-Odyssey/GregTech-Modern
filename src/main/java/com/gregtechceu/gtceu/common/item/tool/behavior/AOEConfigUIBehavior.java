package com.gregtechceu.gtceu.common.item.tool.behavior;

import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.api.item.tool.behavior.IToolUIBehavior;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Stepper;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;
import com.gregtechceu.gtceu.uiwidgets.item.HeldItemPage;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.NotNull;

import static com.gregtechceu.gtceu.api.item.tool.ToolHelper.getBehaviorsTag;
import static com.gregtechceu.gtceu.api.item.tool.ToolHelper.getMaxAoEDefinition;

public class AOEConfigUIBehavior implements IToolUIBehavior {

    public static final AOEConfigUIBehavior INSTANCE = new AOEConfigUIBehavior();

    @Override
    public boolean openUI(@NotNull Player player, @NotNull InteractionHand hand) {
        return player.isShiftKeyDown() && !getMaxAoEDefinition(player.getItemInHand(hand)).isZero();
    }

    @Override
    public ModularUI createUI(Player player, HeldItemUIFactory.HeldItemHolder holder) {
        var max = getMaxAoEDefinition(holder.getHeld());
        return new HeldItemPage(holder, window -> UIElement.section(LayoutStyle.AUTO)
                .layout(l -> l.minWidth(UISizes.CONTENT_WIDTH))
                .addChildren(
                        CoverUIs.controlRow("item.gtceu.tool.aoe.columns", stepper(holder, ToolHelper.AOE_COLUMN_KEY, max.column(), true)),
                        CoverUIs.controlRow("item.gtceu.tool.aoe.rows", stepper(holder, ToolHelper.AOE_ROW_KEY, max.row(), true)),
                        CoverUIs.controlRow("item.gtceu.tool.aoe.layers", stepper(holder, ToolHelper.AOE_LAYER_KEY, max.layer(), false))))
                .noInventory().createUI(player);
    }

    private static Stepper stepper(HeldItemUIFactory.HeldItemHolder holder, String key, int max, boolean symmetric) {
        return new Stepper(UISizes.VALUE_WIDTH, () -> {
            var tag = getBehaviorsTag(holder.getHeld());
            return tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : max;
        }, value -> getBehaviorsTag(holder.getHeld()).putInt(key, Math.clamp(value, 0, max)), 0, max, false,
                value -> Integer.toString(symmetric ? 1 + 2 * value : 1 + value));
    }
}
