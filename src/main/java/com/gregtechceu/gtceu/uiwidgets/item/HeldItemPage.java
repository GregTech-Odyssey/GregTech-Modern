package com.gregtechceu.gtceu.uiwidgets.item;

import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

public final class HeldItemPage implements IFancyUIProvider {

    private static final int SERVER_HEIGHT_LIMIT = Integer.MAX_VALUE / 4;

    private final HeldItemUIFactory.HeldItemHolder holder;
    private final Function<MachineWindow, Widget> content;
    private boolean inventory = true;
    private boolean scroll = true;

    public HeldItemPage(HeldItemUIFactory.HeldItemHolder holder, Function<MachineWindow, Widget> content) {
        this.holder = holder;
        this.content = content;
    }

    public static ModularUI create(HeldItemUIFactory.HeldItemHolder holder, Player player, Function<MachineWindow, Widget> content) {
        return new HeldItemPage(holder, content).createUI(player);
    }

    public HeldItemPage noInventory() {
        this.inventory = false;
        return this;
    }

    public HeldItemPage noScroll() {
        this.scroll = false;
        return this;
    }

    public ModularUI createUI(Player player) {
        return new ModularUI(UISizes.WINDOW_WIDTH, UISizes.WINDOW_WIDTH, holder, player).widget(new MachineWindow(this));
    }

    public ItemStack held() {
        return holder.player.getItemInHand(holder.hand);
    }

    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        var window = (MachineWindow) widget;
        window.setTitleItem(this::held, () -> held().getHoverName());
        var page = content.apply(window);
        if (!scroll) return page;
        var scroller = new ScrollerView("held_item.page", UISizes.CONTENT_WIDTH, UISizes.SLOT).adaptiveWidth().setResizable(false);
        scroller.addScrollViewChild(page);
        scroller.adaptiveHeight(widget.isRemote() ? MachineWindow.clientPageHeightLimit(inventory) : SERVER_HEIGHT_LIMIT);
        return UIElement.column(LayoutStyle.AUTO).addChild(scroller);
    }

    @Override
    public boolean hasPlayerInventory() {
        return inventory;
    }

    @Override
    public IGuiTexture getTabIcon() {
        return new ItemStackTexture(held().copyWithCount(1));
    }

    @Override
    public Component getTitle() {
        return held().getHoverName();
    }
}
