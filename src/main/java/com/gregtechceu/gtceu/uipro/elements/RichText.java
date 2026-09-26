package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.api.gui.widget.CustomComponentPanelWidget;
import com.gregtechceu.gtceu.uipro.ILayoutItem;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.ComponentCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.geometry.FloatSize;
import dev.vfyjxf.taffy.util.MeasureFunc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 服务端逐行下发的富文本（{@code Component} 列表），自动换行，高度随内容变化：多方块的状态显示（{@code addDisplayText}）这类
 * 内容由机器代码逐行拼出来、带颜色和可点击片段的文字。
 * <p>
 * 同步、点击、悬停沿用 GTM {@link CustomComponentPanelWidget}：服务端 {@code textSupplier} 取文字，变化时整表下发，
 * 附加数据经 {@code setTextData}（写、读成对设置）；{@code ComponentPanelWidget.withButton} 做出的片段可点击，
 * 点击交给 {@code clickHandler}（两端各调一次）；带悬停事件的片段悬停时显示说明。
 * <p>
 * 外观按亮底重配：这些文字原本是写给 GTM 深色显示屏的（白字、亮黄、亮青……），在窗口的浅色底上看不清，
 * 所以绘制前把每个字符的颜色换成 {@link UITheme#lightBackgroundColor} 给出的深色版；没有颜色的字用正文色；不画阴影。
 * <p>
 * 布局：宽度由父元素决定（纵向容器里拉伸），高度按该宽度换行后的行数（文字只在客户端测量，服务端按一行估计）。
 */
public class RichText extends CustomComponentPanelWidget implements ILayoutItem {

    /// 行间距
    private static final int LINE_SPACE = 2;

    private final LayoutStyle layoutStyle = LayoutStyle.fixed(LayoutStyle.AUTO, LayoutStyle.AUTO, () -> UIElement.markLayoutDirty(this));
    @Nullable
    private String justifyMark;
    private boolean darkBackground;

    public RichText() {
        super(0, 0);
        this.space = LINE_SPACE;
    }

    public RichText darkBackground() {
        this.darkBackground = true;
        return this;
    }

    public RichText justify(String mark) {
        this.justifyMark = mark;
        return this;
    }

    @Override
    public LayoutStyle getLayoutStyle() {
        return layoutStyle;
    }

    @Override
    public MeasureFunc getLayoutMeasure() {
        return (known, available) -> {
            float width = !Float.isNaN(known.width) ? known.width : available.width.isDefinite() ? available.width.getValue() : UISizes.CONTENT_WIDTH;
            float height = !Float.isNaN(known.height) ? known.height : isRemote() ? measureHeight(Math.max(1, (int) width)) : UISizes.TEXT_HEIGHT;
            return new FloatSize(width, height);
        };
    }

    @OnlyIn(Dist.CLIENT)
    private int measureHeight(int width) {
        wrapTo(width);
        int lines = cacheLines.size();
        return lines == 0 ? UISizes.TEXT_HEIGHT : lines * (Minecraft.getInstance().font.lineHeight + space) - space;
    }

    /** 按宽度重新换行（宽度没变时不重算）。 */
    @OnlyIn(Dist.CLIENT)
    private void wrapTo(int width) {
        if (width == maxWidthLimit) return;
        maxWidthLimit = width;
        formatDisplayText();
    }

    /** 布局写回的宽度就是换行宽度。 */
    @Override
    public void setSize(Size size) {
        super.setSize(size);
        if (isRemote() && size.width > 0) wrapTo(size.width);
    }

    /** 文字变了：父类会按内容改自己的尺寸，这里改为交给布局重新测量。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateComponentTextSize() {
        UIElement.markLayoutDirty(this);
    }

    /** 换行后把颜色换成亮底上的深色版（点击、悬停事件保留在样式里）。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void formatDisplayText() {
        if (justifyMark == null || maxWidthLimit <= 0) {
            super.formatDisplayText();
        } else {
            var font = Minecraft.getInstance().font;
            var lines = new ArrayList<FormattedCharSequence>();
            for (var component : lastText) lines.addAll(justify(component, font));
            cacheLines = lines;
        }
        cacheLines = cacheLines.stream().map(this::recolor).toList();
    }

    @OnlyIn(Dist.CLIENT)
    private List<FormattedCharSequence> justify(Component component, Font font) {
        int[] widths = new int[2];
        boolean[] marked = new boolean[1];
        component.visit((style, text) -> {
            if (text.equals(justifyMark)) marked[0] = true;
            else widths[marked[0] ? 1 : 0] += font.width(text);
            return Optional.empty();
        }, Style.EMPTY);
        var collector = new ComponentCollector();
        component.visit((style, text) -> {
            collector.append(FormattedText.of(text.equals(justifyMark) ? leader(font, maxWidthLimit - widths[0] - widths[1]) : text, style));
            return Optional.empty();
        }, Style.EMPTY);
        var lines = new ArrayList<FormattedCharSequence>();
        font.getSplitter().splitLines(collector.getResultOrEmpty(), maxWidthLimit, Style.EMPTY,
                (text, continued) -> lines.add(Language.getInstance().getVisualOrder(text)));
        return lines.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : lines;
    }

    @OnlyIn(Dist.CLIENT)
    private String leader(Font font, int space) {
        int markWidth = font.width(justifyMark);
        if (space <= 0 || markWidth <= 0) return " ";
        int count = space / markWidth;
        while (font.width(justifyMark.repeat(count) + " ") <= space) count++;
        return justifyMark.repeat(Math.max(0, count - 2)) + " ";
    }

    private FormattedCharSequence recolor(FormattedCharSequence line) {
        return sink -> line.accept((index, style, codePoint) -> sink.accept(index, darkBackground ? darkStyle(style) : lightStyle(style), codePoint));
    }

    private static Style darkStyle(Style style) {
        var color = style.getColor();
        int rgb = color == null ? UITheme.SCREEN_TEXT & 0xFFFFFF : UITheme.darkBackgroundColor(color.getValue());
        return style.withColor(TextColor.fromRgb(rgb));
    }

    private static Style lightStyle(Style style) {
        var color = style.getColor();
        int rgb = color == null ? UITheme.TEXT & 0xFFFFFF : UITheme.lightBackgroundColor(color.getValue());
        return style.withColor(TextColor.fromRgb(rgb));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return isMouseOverElement(mouseX, mouseY) && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (isMouseOverElement(mouseX, mouseY)) super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawBackgroundTexture(graphics, mouseX, mouseY);
        var font = Minecraft.getInstance().font;
        int x = getPositionX(), y = getPositionY();
        for (int i = 0; i < cacheLines.size(); i++) {
            graphics.drawString(font, cacheLines.get(i), x, y + i * (font.lineHeight + space), UITheme.TEXT, false);
        }
    }
}
