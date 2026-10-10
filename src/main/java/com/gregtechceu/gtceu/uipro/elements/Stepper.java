package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.RPC;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.render.UIStates;
import com.gregtechceu.gtceu.uipro.render.UIText;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.gto.datasynclib.datastream.codec.ByteBufCodecs;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * 步进器 {@code [<] 值 [>]}：左右按钮或在数值上滚动鼠标滚轮来改变整数，适合电路编号、页码这类小范围选择。
 * <p>
 * 标准高 {@link #HEIGHT}，总宽 {@link #widthFor(int)}（两个方形箭头 + 中间数值框 + 间距）。
 * 数值以服务端为准：点击/滚轮都在服务端计算新值后写入 {@code setter}，再经同步回显；上限可随服务端状态变化。
 * {@link #wrap()} 后越过边界回绕（32 的下一个是 0），否则停在边界，箭头禁用（统一斜纹，悬停说明已到边界）。
 * 整个步进器禁用（{@link #disabled}，或上级禁用）时，两个箭头和滚轮都不起作用。
 */
public class Stepper extends UIElement {

    private static final String AT_MIN = "gtceu.uipro.stepper.at_min";
    private static final String AT_MAX = "gtceu.uipro.stepper.at_max";

    public static final int HEIGHT = UISizes.CONTROL_HEIGHT;

    private final IntSupplier getter;
    private final IntConsumer setter;
    private final int min;
    private final IntSupplier max;
    private boolean wrap;
    private IntFunction<String> formatter = Integer::toString;
    private final SyncValue<Integer> value;
    private final RPC<Boolean> wheel = addRPC(ByteBufCodecs.BOOL, (player, up) -> step(up ? 1 : -1));

    protected Stepper(int valueWidth, IntSupplier getter, IntConsumer setter, int min, IntSupplier max) {
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.value = addSyncValue(SyncValue.ofInt(getter::getAsInt, getter.getAsInt()));
        layout(l -> l.row().height(HEIGHT).gapAll(UISizes.GAP).alignCenter());
        addChildren(
                Button.icon(UITheme.ARROW_LEFT).setOnServerClick(() -> step(-1)).disabled(() -> !wrap && getter.getAsInt() <= min, AT_MIN),
                new ValueBox(valueWidth),
                Button.icon(UITheme.ARROW_RIGHT).setOnServerClick(() -> step(1)).disabled(() -> !wrap && getter.getAsInt() >= max.getAsInt(), AT_MAX));
    }

    public static Stepper of(int valueWidth, IntSupplier getter, IntConsumer setter, int min, int max) {
        return new Stepper(valueWidth, getter, setter, min, () -> max);
    }

    public static Stepper of(int valueWidth, IntSupplier getter, IntConsumer setter, int min, IntSupplier max) {
        return new Stepper(valueWidth, getter, setter, min, max);
    }

    public Stepper wrap() {
        this.wrap = true;
        return this;
    }

    public Stepper setFormatter(IntFunction<String> formatter) {
        this.formatter = formatter;
        return this;
    }

    public static int widthFor(int valueWidth) {
        return 2 * UISizes.ICON_BUTTON_SIZE + 2 * UISizes.GAP + valueWidth;
    }

    private void step(int delta) {
        int upper = Math.max(min, max.getAsInt());
        int next = getter.getAsInt() + delta;
        if (wrap) next = Math.floorMod(next - min, upper - min + 1) + min;
        else next = Math.clamp(next, min, upper);
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
            wheel.send(wheelDelta > 0);
            return true;
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
            UITheme.INSET.draw(graphics, UIStates.NONE, x, y, w, h);
            UIText.drawCentered(graphics, formatter.apply(value.getValue()), x + w / 2, UIText.centerY(y, h), w - 4, UITheme.FIELD_TEXT);
            if (isDisabled()) UIDraw.disabledHatch(graphics, x, y, w, h);
        }
    }
}
