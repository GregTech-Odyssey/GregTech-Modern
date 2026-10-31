package com.gregtechceu.gtceu.uiwidgets.cover;

import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Adjuster;
import com.gregtechceu.gtceu.uipro.elements.ButtonGroup;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CoverUIs {

    private CoverUIs() {}

    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "27.0")
    public static UIElement controlRow(Component label, Widget control, Component... tooltips) {
        return Form.controlRow(label, control, tooltips);
    }

    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "27.0")
    public static UIElement inlineNumberRow(Component label, Adjuster field, Component... tooltips) {
        return Form.inlineNumberRow(label, field, tooltips);
    }

    public static <E extends EnumSelectorWidget.SelectableEnum> ButtonGroup enumIcons(List<E> values, Supplier<E> current, Consumer<E> set) {
        return ButtonGroup.singleIcons(values.size(), i -> values.get(i).getIcon(), () -> values.indexOf(current.get()), i -> set.accept(values.get(i)))
                .optionTooltips(i -> LangHandler.getSingleOrMultiLang(values.get(i).getTooltip()));
    }

    public static <E extends EnumSelectorWidget.SelectableEnum> UIElement enumRow(Component label, List<E> values, Supplier<E> current,
                                                                                  Consumer<E> set, Component... tooltips) {
        return UIElement.centeredRow(UISizes.SLOT_SIZE)
                .addChildren(Form.label(label, tooltips), enumIcons(values, current, set));
    }

    public static UIElement filterSection(FilterHandler<?, ?> handler) {
        var name = TextLine.of(0, handler::getFilterName).bindClientColor(UITheme::panelText);
        name.layout(l -> l.flex(1));
        var head = UIElement.row(UISizes.SLOT_SIZE).layout(l -> l.gapAll(UISizes.SECTION_GAP).alignCenter())
                .addChildren(handler.createFilterSlot(), name);
        return UIElement.section().addChildren(head, handler.createFilterConfig());
    }
}
