package com.gregtechceu.gtceu.uiwidgets.cover;

import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;

public record CoverPage(IUICover cover) implements IFancyUIProvider {

    private static final int SERVER_HEIGHT_LIMIT = Integer.MAX_VALUE / 4;

    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        if (widget instanceof MachineWindow window) window.setTitleItem(() -> cover.self().getAttachItem(), cover::getCoverTitle);
        var scroller = new ScrollerView("cover.page", UISizes.CONTENT_WIDTH, UISizes.SLOT).adaptiveWidth().setResizable(false);
        scroller.addScrollViewChild(cover.createUIWidget());
        scroller.adaptiveHeight(widget.isRemote() ? MachineWindow.clientPageHeightLimit(hasPlayerInventory()) : SERVER_HEIGHT_LIMIT);
        return UIElement.column(LayoutStyle.AUTO).addChild(scroller);
    }

    @Override
    public IGuiTexture getTabIcon() {
        return new ItemStackTexture(cover.self().getAttachItem());
    }

    @Override
    public Component getTitle() {
        return cover.getCoverTitle();
    }
}
