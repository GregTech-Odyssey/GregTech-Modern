package com.gregtechceu.gtceu.uiwidgets.cover;

import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Adjuster;
import com.gregtechceu.gtceu.uipro.elements.ButtonGroup;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CoverUIs {

    private CoverUIs() {}

    public static UIElement page() {
        return UIElement.column(LayoutStyle.AUTO).layout(l -> l.minWidth(UISizes.CONTENT_WIDTH).gapAll(UISizes.SECTION_GAP));
    }

    public static UIElement section(String titleKey) {
        return UIElement.section().addChild(TextLine.translatable(LayoutStyle.AUTO, titleKey).setColor(UITheme.PANEL_TEXT));
    }

    public static TextLine label(String labelKey, String... tooltipKeys) {
        var label = TextLine.translatable(0, labelKey).setColor(UITheme.PANEL_TEXT);
        label.layout(l -> l.flex(1));
        if (tooltipKeys.length > 0) label.setHoverTooltips(tooltipKeys);
        return label;
    }

    public static UIElement controlRow(String labelKey, Widget control, String... tooltipKeys) {
        return UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter())
                .addChildren(label(labelKey, tooltipKeys), control);
    }

    public static UIElement numberRow(String labelKey, Adjuster field, String... tooltipKeys) {
        var label = TextLine.translatable(LayoutStyle.AUTO, labelKey).setColor(UITheme.PANEL_TEXT);
        if (tooltipKeys.length > 0) label.setHoverTooltips(tooltipKeys);
        return UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP)).addChildren(label, field);
    }

    public static <E extends EnumSelectorWidget.SelectableEnum> ButtonGroup enumIcons(List<E> values, Supplier<E> current, Consumer<E> set) {
        return ButtonGroup.singleIconsLines(values.size(), i -> values.get(i).getIcon(),
                i -> LangHandler.getSingleOrMultiLang(values.get(i).getTooltip()).toArray(new Component[0]),
                () -> values.indexOf(current.get()), i -> set.accept(values.get(i)));
    }

    public static <E extends EnumSelectorWidget.SelectableEnum> UIElement enumRow(String labelKey, List<E> values, Supplier<E> current,
                                                                                  Consumer<E> set, String... tooltipKeys) {
        return UIElement.row(UISizes.SLOT).layout(l -> l.gapAll(UISizes.GAP).alignCenter())
                .addChildren(label(labelKey, tooltipKeys), enumIcons(values, current, set));
    }

    public static UIElement filterSection(FilterHandler<?, ?> handler) {
        var name = TextLine.of(0, handler::getFilterName).setColor(UITheme.PANEL_TEXT);
        name.layout(l -> l.flex(1));
        var head = UIElement.row(UISizes.SLOT).layout(l -> l.gapAll(UISizes.SECTION_GAP).alignCenter())
                .addChildren(handler.createFilterSlot(), name);
        return UIElement.section().addChildren(head, handler.createFilterConfig());
    }
}
