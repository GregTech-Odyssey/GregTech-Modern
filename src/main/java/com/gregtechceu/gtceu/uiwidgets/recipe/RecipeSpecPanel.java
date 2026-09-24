package com.gregtechceu.gtceu.uiwidgets.recipe;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.AlignItems;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * 配方页的"参数表"：一块状态显示窗（{@link UITheme#STATUS_PANEL}），自上而下是
 *
 * <pre>
 *  超频预览                 (i) [&lt; LV &gt;]    标题行：左侧标题，右侧控件（可省）
 *  ────────────────────────────────────
 *  耗时                         21.9 秒     数值行：名称靠左、数值靠右，隔行加底纹方便横向对行
 *  耗能功率          30 EU/t  1 A @ LV      数值后可跟一段次要说明（灰色）
 *  ────────────────────────────────────
 *  ▪ 可通过科技节点[太空电梯]解锁             说明行：配方条件等整句，前面一个小方块
 * </pre>
 *
 * 行高固定（{@link #ROW_HEIGHT}），面板高度可以不建控件就算出（{@link #heightFor}）。文字放不下时截断，悬停看全文。
 * 数值由 {@link SyncValue} 取值（在配方查看器这类本地界面里直接取本端 getter）；绘制用的字符串和截断结果按文字与宽度缓存，不每帧重算。
 */
public class RecipeSpecPanel extends UIElement {

    public static final int ROW_HEIGHT = UISizes.STATUS_LINE_HEIGHT;
    public static final int HEADER_HEIGHT = UISizes.CONTROL_HEIGHT;
    /// 分隔线：1 像素线，上下各留 1 像素
    public static final int DIVIDER_HEIGHT = 3;
    private static final int DIVIDER_COLOR = 0xFF8B8B8B;
    /// 隔行底纹、行内文字离底纹左右边的距离
    private static final int STRIPE_COLOR = 0x1A000000;
    private static final int ROW_INSET = 2;
    /// 说明行前的小方块
    private static final int BULLET = 3;
    private static final int BULLET_SPACE = BULLET + 3;
    private static final int TEXT_LINE = 8;

    private int rows;

    public RecipeSpecPanel() {
        layout(l -> l.column().paddingAll(UITheme.PANEL_PADDING).paddingBottom(UITheme.PANEL_PADDING_BOTTOM));
        setBackground(UITheme.STATUS_PANEL);
    }

    /** 面板高度：有无标题行、数值行数、说明行数（两组都有时中间一条分隔线）。 */
    public static int heightFor(boolean header, int values, int sentences) {
        int height = UITheme.PANEL_PADDING + UITheme.PANEL_PADDING_BOTTOM + (values + sentences) * ROW_HEIGHT;
        if (header) height += HEADER_HEIGHT + DIVIDER_HEIGHT;
        if (values > 0 && sentences > 0) height += DIVIDER_HEIGHT;
        return height;
    }

    /**
     * 标题行：左侧标题，右侧 {@code controls}，下面接一条分隔线。应在所有行之前调用。
     * 控件优先完整显示；标题占剩下的宽度，放不下时截断（悬停看全文），不会把面板撑宽。
     */
    public RecipeSpecPanel header(Component title, Widget... controls) {
        var row = new UIElement().layout(l -> l.row().height(HEADER_HEIGHT).gapAll(UISizes.GAP).alignCenter().alignSelf(AlignItems.STRETCH));
        row.addChild(new Title(title));
        row.addChildren(controls);
        addChild(row);
        return divider();
    }

    /** 一条分隔线（数值行与说明行之间）。 */
    public RecipeSpecPanel divider() {
        addChild(new Divider());
        return this;
    }

    /** 数值行："名称 …… 数值 次要说明"。{@code detail} 可为 null。 */
    public RecipeSpecPanel value(Component label, Supplier<Component> value, @Nullable Supplier<Component> detail) {
        addChild(new Row(label, value, detail, rows++ % 2 == 1));
        return this;
    }

    /** 说明行：一整句。 */
    public RecipeSpecPanel sentence(Supplier<Component> text) {
        addChild(new Row(null, text, null, false));
        return this;
    }

    /** 一段文字的字符串与宽度，文字对象不变时复用；截断结果按可用宽度复用。 */
    private static final class CachedText {

        @Nullable
        private Component source;
        private String text = "";
        private int width;
        private int clipWidth = -1;
        private String clipped = "";

        @OnlyIn(Dist.CLIENT)
        private CachedText update(Font font, Component component) {
            if (component != source) {
                source = component;
                text = component.getString();
                width = font.width(text);
                clipWidth = -1;
            }
            return this;
        }

        @OnlyIn(Dist.CLIENT)
        private String clip(Font font, int maxWidth) {
            if (maxWidth != clipWidth) {
                clipWidth = maxWidth;
                clipped = UITheme.clip(font, text, maxWidth);
            }
            return clipped;
        }
    }

    /** 标题：占控件左侧剩下的宽度，可以收缩到 0；放不下时截断，悬停显示全文。 */
    private static final class Title extends UIElement {

        private final Component text;
        private final CachedText cache = new CachedText();

        private Title(Component text) {
            this.text = text;
            layout(l -> l.flexGrow(1).flexShrink(1).minWidth(0).height(HEADER_HEIGHT));
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            var font = Minecraft.getInstance().font;
            cache.update(font, text);
            graphics.drawString(font, cache.clip(font, getSizeWidth()), getPositionX(), getPositionY() + (HEADER_HEIGHT - TEXT_LINE) / 2,
                    UITheme.TEXT_SECONDARY, false);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
            if (gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
            if (cache.width > getSizeWidth()) gui.getModularUIGui().setHoverTooltip(List.of(text), ItemStack.EMPTY, null, null);
        }
    }

    private static final class Divider extends UIElement {

        private Divider() {
            layout(l -> l.height(DIVIDER_HEIGHT).alignSelf(AlignItems.STRETCH));
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int y = getPositionY() + DIVIDER_HEIGHT / 2;
            graphics.fill(getPositionX(), y, getPositionX() + getSizeWidth(), y + 1, DIVIDER_COLOR);
        }
    }

    private static final class Row extends UIElement {

        /// null 为说明行
        @Nullable
        private final Component label;
        private final SyncValue<Component> value;
        @Nullable
        private final SyncValue<Component> detail;
        private final boolean striped;
        private final CachedText labelText = new CachedText();
        private final CachedText valueText = new CachedText();
        private final CachedText detailText = new CachedText();
        /// 上次绘制时有没有截断或省略（悬停据此显示整行）
        private boolean truncated;

        private Row(@Nullable Component label, Supplier<Component> value, @Nullable Supplier<Component> detail, boolean striped) {
            this.label = label;
            this.striped = striped;
            layout(l -> l.height(ROW_HEIGHT).alignSelf(AlignItems.STRETCH));
            this.value = addSyncValue(SyncValue.of(value, SyncValue.COMPONENT, Component.empty()));
            this.detail = detail == null ? null : addSyncValue(SyncValue.of(detail, SyncValue.COMPONENT, Component.empty()));
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x = getPositionX(), y = getPositionY(), width = getSizeWidth();
            if (striped) graphics.fill(x, y, x + width, y + ROW_HEIGHT, STRIPE_COLOR);
            var font = Minecraft.getInstance().font;
            int textY = y + (ROW_HEIGHT - TEXT_LINE) / 2;
            int left = x + ROW_INSET, inner = width - 2 * ROW_INSET;
            var shownValue = valueText.update(font, value.getValue());
            if (label == null) {
                int bulletY = y + (ROW_HEIGHT - BULLET) / 2;
                graphics.fill(left, bulletY, left + BULLET, bulletY + BULLET, UITheme.TEXT_SECONDARY);
                truncated = shownValue.width > inner - BULLET_SPACE;
                graphics.drawString(font, shownValue.clip(font, inner - BULLET_SPACE), left + BULLET_SPACE, textY, UITheme.TEXT, false);
                return;
            }
            // 整行放得下就全部显示；放不下时先省略次要说明，数值最多占 2/3，名称用剩下的宽度（截断）
            var shownDetail = detail == null ? null : detailText.update(font, detail.getValue());
            int labelWidth = labelText.update(font, label).width;
            int fullDetail = shownDetail == null || shownDetail.text.isEmpty() ? 0 : UISizes.TEXT_PADDING + shownDetail.width;
            int valueWidth = Math.min(shownValue.width, inner * 2 / 3);
            int detailWidth = labelWidth + UISizes.TEXT_PADDING + valueWidth + fullDetail <= inner ? fullDetail : 0;
            int labelSpace = inner - valueWidth - detailWidth - UISizes.TEXT_PADDING;
            truncated = detailWidth < fullDetail || valueWidth < shownValue.width || labelWidth > labelSpace;
            var shownLabel = labelText.clip(font, labelSpace);
            graphics.drawString(font, shownLabel, left, textY, UITheme.TEXT_SECONDARY, false);
            int right = left + inner;
            if (detailWidth > 0) graphics.drawString(font, shownDetail.text, right - shownDetail.width, textY, UITheme.TEXT_SECONDARY, false);
            String clippedValue = shownValue.clip(font, valueWidth);
            graphics.drawString(font, clippedValue, right - detailWidth - font.width(clippedValue), textY, UITheme.TEXT, false);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
            if (gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
            // 截断或省略了次要说明时，悬停显示整行
            if (!truncated) return;
            boolean hasDetail = detail != null && !detailText.text.isEmpty();
            var full = label == null ? value.getValue().copy() : label.copy().append("  ").append(value.getValue());
            if (hasDetail) full.append("  ").append(detail.getValue());
            gui.getModularUIGui().setHoverTooltip(List.of(full), ItemStack.EMPTY, null, null);
        }
    }
}
