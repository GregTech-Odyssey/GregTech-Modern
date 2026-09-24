package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

/**
 * 单行文字：高度固定为 {@link #HEIGHT}，放不下时截断并加省略号（鼠标停上去显示全文）。宽度固定，或传
 * {@link com.gregtechceu.gtceu.uipro.LayoutStyle#AUTO} 由布局决定（纵向容器里被拉伸；横向行里配 {@code flex(1)} 吃满剩余宽度）。
 * <p>
 * 与 {@link Label} 的区别：{@code Label} 在客户端按文字实际宽高重排自身尺寸（空串只有 2 像素高、长文字会换行），
 * 而窗口尺寸只在建页时算一次，页面建好后才下发的文字会让布局溢出。{@code TextLine} 的尺寸在构造时就确定、
 * 与文字内容无关（不按文字测量），适合显示名称、状态这类由服务端下发、长度不定的单行文字。
 * <p>
 * 高度取 {@link UISizes#TEXT_HEIGHT}（字体行高 9，与单行 {@code Label} 同高），这样它能和 {@code Label} 在区块里混排、行距一致；
 * 放进 {@link UISizes#CONTROL_HEIGHT} 高的控件行时用 {@code alignCenter()} 居中。
 * <p>
 * 文字由服务端取值下发（{@link SyncValue}）；构造时<strong>不会</strong>调用 getter，客户端显示下发前为空，
 * 所以 getter 可以放心依赖服务端独有的数据。两端文字相同的静态文字用 {@link #constant}。
 */
public class TextLine extends UIElement {

    /** 标准高度：单行文字高度。 */
    public static final int HEIGHT = UISizes.TEXT_HEIGHT;

    private final SyncValue<Component> text;
    private int color = UITheme.TEXT;
    private float scale = 1;

    public TextLine(int width, Supplier<Component> text, Component initial) {
        layout(l -> l.size(width, HEIGHT));
        this.text = addSyncValue(SyncValue.of(text, SyncValue.COMPONENT, initial));
    }

    /** 服务端取值下发的文字。 */
    public static TextLine of(int width, Supplier<Component> text) {
        return new TextLine(width, text, Component.empty());
    }

    /** 两端相同的固定文字（客户端一开始就显示，不等下发）。 */
    public static TextLine constant(int width, Component text) {
        return new TextLine(width, () -> text, text);
    }

    public static TextLine translatable(int width, String key) {
        return constant(width, Component.translatable(key));
    }

    /** 小字（{@link UISizes#SMALL_TEXT_SCALE} 倍，高 {@link UISizes#SMALL_TEXT_HEIGHT}），用于列表里的次要信息。 */
    public TextLine setSmall() {
        this.scale = UISizes.SMALL_TEXT_SCALE;
        layout(l -> l.height(UISizes.SMALL_TEXT_HEIGHT));
        return this;
    }

    /** 文字颜色，取 {@link UITheme} 里的颜色。 */
    public TextLine setColor(int color) {
        this.color = color;
        return this;
    }

    /** 最近一次同步到的文字。 */
    public Component getText() {
        return text.getValue();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        var font = Minecraft.getInstance().font;
        var clipped = UITheme.clip(font, text.getValue().getString(), (int) (getSizeWidth() / scale));
        var pose = graphics.pose();
        pose.pushPose();
        // 整数偏移，避免文字落在半像素上发虚
        pose.translate(getPositionX(), getPositionY() + (float) (getSizeHeight() - Math.round(8 * scale)) / 2, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, clipped, 0, 0, color, false);
        pose.popPose();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        // 自带提示优先；没有提示且文字被截断时，悬停显示全文
        if (!tooltipTexts.isEmpty() || gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
        var font = Minecraft.getInstance().font;
        var full = text.getValue();
        if (font.width(full.getString()) * scale > getSizeWidth()) {
            gui.getModularUIGui().setHoverTooltip(List.of(full), ItemStack.EMPTY, null, null);
        }
    }
}
