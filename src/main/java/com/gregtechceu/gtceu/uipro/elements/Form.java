package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.render.UIText;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * 设置页的标准拼法：整页纵向排布、带标题的区块、"名称 + 控件"一行、"名称 + 数值调节器"一行。
 */
public final class Form {

    private Form() {}

    public static UIElement page() {
        return UIElement.column(LayoutStyle.AUTO).layout(l -> l.minWidth(UISizes.CONTENT_WIDTH).gapAll(UISizes.SECTION_GAP));
    }

    public static UIElement section(String titleKey) {
        return UIElement.section().addChild(TextLine.translatable(LayoutStyle.AUTO, titleKey).bindClientColor(UITheme::panelText));
    }

    public static TextLine label(String labelKey, String... tooltipKeys) {
        var label = TextLine.translatable(0, labelKey).bindClientColor(UITheme::panelText);
        label.layout(l -> l.flex(1));
        if (tooltipKeys.length > 0) label.tooltips(tooltipKeys);
        return label;
    }

    public static TextLine fieldLabel(String labelKey, String... tooltipKeys) {
        var label = TextLine.translatable(LayoutStyle.AUTO, labelKey).bindClientColor(UITheme::panelText);
        if (tooltipKeys.length > 0) label.tooltips(tooltipKeys);
        return label;
    }

    public static UIElement controlRow(String labelKey, Widget control, String... tooltipKeys) {
        return controlRow(UISizes.CONTROL_HEIGHT, labelKey, control, tooltipKeys);
    }

    public static UIElement controlRow(int height, String labelKey, Widget control, String... tooltipKeys) {
        return UIElement.centeredRow(height).addChildren(label(labelKey, tooltipKeys), control);
    }

    public static UIElement numberRow(String labelKey, Adjuster field, String... tooltipKeys) {
        return UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP)).addChildren(fieldLabel(labelKey, tooltipKeys), field);
    }

    public static UIElement inlineNumberRow(String labelKey, Adjuster field, String... tooltipKeys) {
        var label = fieldLabel(labelKey, tooltipKeys);
        var row = new UIElement().addChildren(label, field);
        if (field.getInlineWidth() <= UISizes.INLINE_ADJUSTER_WIDTH && fitsInline(label, UISizes.CONTENT_WIDTH - 2 * UISizes.PANEL_PADDING)) {
            placeInline(label, field);
            return row.layout(l -> l.row().height(UISizes.CONTROL_HEIGHT).gapAll(UISizes.GAP).alignCenter());
        }
        return row.layout(l -> l.column().width(LayoutStyle.AUTO).gapAll(UISizes.GAP));
    }

    public static InlineNumberRows inlineNumberRows(int innerWidth, int rowHeight) {
        return new InlineNumberRows(innerWidth, rowHeight);
    }

    private static boolean fitsInline(TextLine label, int innerWidth) {
        int labelWidth = GTCEu.isClientThread() ? clientTextWidth(label.getText()) : 0;
        return labelWidth + UISizes.GAP + UISizes.INLINE_ADJUSTER_WIDTH <= innerWidth;
    }

    private static void placeInline(TextLine label, Adjuster field) {
        label.layout(l -> l.width(0).flex(1));
        field.layout(l -> l.width(UISizes.INLINE_ADJUSTER_WIDTH));
    }

    @OnlyIn(Dist.CLIENT)
    private static int clientTextWidth(Component text) {
        return UIText.width(text);
    }

    /**
     * 一组"名称 + 数值调节器"行：步进按钮统一 {@link UISizes#STEP_BUTTON_WIDTH} 宽；只要有一行放不下，整组都改成名称在上、调节器在下。
     */
    public static final class InlineNumberRows {

        private record Entry(UIElement row, TextLine label, Adjuster field, boolean fits) {}

        private final int innerWidth;
        private final int rowHeight;
        private final List<Entry> entries = new ArrayList<>();
        private boolean finished;

        private InlineNumberRows(int innerWidth, int rowHeight) {
            this.innerWidth = innerWidth;
            this.rowHeight = rowHeight;
        }

        public UIElement add(String labelKey, Adjuster field, String... tooltipKeys) {
            var label = fieldLabel(labelKey, tooltipKeys);
            field.setStepButtonMinWidth(UISizes.STEP_BUTTON_WIDTH);
            var row = new UIElement();
            entries.add(new Entry(row, label, field, fitsInline(label, innerWidth)));
            return row;
        }

        public void finish() {
            if (finished) return;
            finished = true;
            boolean wrap = false;
            for (var entry : entries) wrap |= !entry.fits;
            for (var entry : entries) {
                if (wrap) {
                    entry.field.layout(l -> l.width(LayoutStyle.AUTO));
                    entry.row.layout(l -> l.column().width(LayoutStyle.AUTO).gapAll(1).paddingVertical(1)).addChildren(entry.label, entry.field);
                } else {
                    placeInline(entry.label, entry.field);
                    entry.row.layout(l -> l.row().width(LayoutStyle.AUTO).height(rowHeight).gapAll(UISizes.GAP).alignCenter()).addChildren(entry.label, entry.field);
                }
            }
        }
    }
}
