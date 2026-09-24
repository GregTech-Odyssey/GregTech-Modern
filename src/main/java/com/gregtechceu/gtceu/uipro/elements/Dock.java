package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 悬浮栏（dock）：浮在某块内容（如三视图）之上的一条工具栏，与窗口同色的圆角面板（{@link UITheme#drawDock}，不画投影），
 * 里面一组组控件横排，组与组之间一条细分隔线。宽高随内容，由使用方摆到内容的底部居中（离底边 {@link UISizes#DOCK_MARGIN}）。
 * <p>
 * 栏本身挡住下面的内容：点在栏上（包括控件之间的空隙）不会落到下面（例如三视图的选面）。
 * <p>
 * 可选强调色（{@link #setAccentColor}）：栏里的设置只作用于某个对象时，外框用与该对象高亮相同的颜色，把两者在视觉上连起来。
 */
public class Dock extends UIElement {

    private final boolean vertical;
    private boolean empty = true;
    /// 强调色（ARGB），0 为不用：画普通的深色外框 + 白色高光
    private int accentColor;

    /** 横向悬浮栏（组从左往右排）。 */
    public Dock() {
        this(false);
    }

    private Dock(boolean vertical) {
        this.vertical = vertical;
        layout(l -> (vertical ? l.column() : l.row()).gapAll(UISizes.SECTION_GAP).paddingAll(UITheme.DOCK_PADDING).alignCenter());
    }

    /** 竖向悬浮栏（组从上往下排，分隔线横着）。 */
    public static Dock vertical() {
        return new Dock(true);
    }

    /** 加一组控件；不是第一组时先加一条分隔线。 */
    public Dock addGroup(Widget group) {
        if (!empty) addChild(new Separator(vertical));
        empty = false;
        addChild(group);
        return this;
    }

    public boolean isEmpty() {
        return empty;
    }

    /**
     * 外框强调色（ARGB，颜色从 {@link UITheme} 取）：栏里的设置只作用于某个被高亮的对象时，外框用与它相同的颜色
     * （例如方向配置页底部的栏用 {@link UITheme#SELECTION_COLOR}，与三视图里选中面的描边一致）。传 0 恢复普通外框。
     */
    public Dock setAccentColor(int argb) {
        this.accentColor = argb;
        return this;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (empty) return;
        UITheme.drawDock(graphics, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), accentColor);
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button) || !empty && isMouseOver(getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), mouseX, mouseY);
    }

    /** 组间分隔线：横向栏里 1 像素宽、与槽同高；竖向栏里与槽同宽、1 像素高。两端各缩进 2 像素。 */
    private static final class Separator extends Widget {

        private Separator(boolean horizontalLine) {
            super(0, 0, horizontalLine ? UISizes.SLOT : 1, horizontalLine ? 1 : UISizes.SLOT);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
            if (w > h) graphics.fill(x + 2, y, x + w - 2, y + 1, UITheme.DOCK_SEPARATOR);
            else graphics.fill(x, y + 2, x + 1, y + h - 2, UITheme.DOCK_SEPARATOR);
        }
    }
}
