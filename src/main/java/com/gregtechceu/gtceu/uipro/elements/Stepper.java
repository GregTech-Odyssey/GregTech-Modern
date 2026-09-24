package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * 步进器 {@code [<] 值 [>]}：左右按钮或在数值上滚动鼠标滚轮来改变整数，适合电路编号、页码这类小范围选择。
 * <p>
 * 标准高 {@link #HEIGHT}，总宽 {@link #width(int)}（两个方形箭头 + 中间数值框 + 间距）。
 * 数值以服务端为准：点击/滚轮都在服务端计算新值后写入 {@code setter}，再经同步回显。
 * {@code wrap} 为真时越过边界回绕（32 的下一个是 0），否则停在边界，箭头禁用（统一斜纹，悬停说明已到边界）。
 * 整个步进器禁用（{@link #disabled}，或上级禁用）时，两个箭头和滚轮都不起作用。
 */
public class Stepper extends UIElement {

    private static final String AT_MIN = "gtceu.uipro.stepper.at_min";
    private static final String AT_MAX = "gtceu.uipro.stepper.at_max";

    public static final int HEIGHT = UISizes.CONTROL_HEIGHT;

    private static final int WHEEL_ID = SyncValueHost.ID_BASE - 2;

    private final IntSupplier getter;
    private final IntConsumer setter;
    private final int min;
    private final int max;
    private final boolean wrap;
    private final IntFunction<String> formatter;
    private final SyncValue<Integer> value;

    public Stepper(int valueWidth, IntSupplier getter, IntConsumer setter, int min, int max, boolean wrap, IntFunction<String> formatter) {
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.wrap = wrap;
        this.formatter = formatter;
        this.value = addSyncValue(SyncValue.ofInt(getter::getAsInt, getter.getAsInt()));
        layout(l -> l.row().height(HEIGHT).gapAll(UISizes.GAP).alignCenter());
        addChildren(
                Button.icon(UITheme.ARROW_LEFT).setOnServerClick(() -> step(-1)).disabled(() -> !wrap && getter.getAsInt() <= min, AT_MIN),
                new ValueBox(valueWidth),
                Button.icon(UITheme.ARROW_RIGHT).setOnServerClick(() -> step(1)).disabled(() -> !wrap && getter.getAsInt() >= max, AT_MAX));
    }

    /** 中间数值框宽 {@code valueWidth} 时的总宽度。 */
    public static int width(int valueWidth) {
        return 2 * UISizes.ICON_BUTTON + 2 * UISizes.GAP + valueWidth;
    }

    private void step(int delta) {
        int next = getter.getAsInt() + delta;
        if (wrap) next = Math.floorMod(next - min, max - min + 1) + min;
        else next = Math.clamp(next, min, max);
        setter.accept(next);
    }

    private final class ValueBox extends Widget {

        private ValueBox(int width) {
            super(0, 0, width, HEIGHT);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
            if (!isMouseOverElement(mouseX, mouseY) || wheelDelta == 0 || isDisabled()) return false;
            int delta = wheelDelta > 0 ? 1 : -1;
            writeClientAction(WHEEL_ID, buf -> buf.writeVarInt(delta));
            return true;
        }

        @Override
        public void handleClientAction(int id, FriendlyByteBuf buffer) {
            // 服务端再判一次禁用（客户端可以伪造请求）
            if (id == WHEEL_ID) {
                int delta = buffer.readVarInt();
                if (!isDisabled()) step(delta > 0 ? 1 : -1);
            } else super.handleClientAction(id, buffer);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
            UITheme.drawInset(graphics, x, y, w, h, false);
            UITheme.drawCenteredText(graphics, formatter.apply(value.getValue()), x + w / 2, y + (h - 8) / 2, w - 4, UITheme.FIELD_TEXT, false);
            if (isDisabled()) UITheme.drawDisabled(graphics, x, y, w, h);
        }
    }
}
