package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.render.UIPixels;
import com.gregtechceu.gtceu.uipro.render.UIStates;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 指示灯：与按钮、输入框同高的方块（{@link #SIZE} 见方），深色框里一颗灯，显示若干状态之一，
 * 每个状态一种颜色、一段悬浮提示。当前状态由服务端取值下发（{@code state} 返回状态下标，越界时灯显示为灰色）。
 * 
 * <pre>
 * Indicator.of(() -&gt; online ? 1 : 0,
 *         Indicator.State.of(0xFFDD4444, "…offline"),
 *         Indicator.State.of(0xFF55DD55, "…online"));
 * </pre>
 */
public class Indicator extends UIElement {

    public static final int SIZE = UISizes.CONTROL_HEIGHT;
    private static final int LAMP = 8;
    private static final int UNKNOWN_COLOR = 0xFF808080;

    /** 一个状态：灯的颜色与悬浮提示（提示在客户端取值）。 */
    public record State(int color, Supplier<Component> tooltip) {

        public static State of(int color, String translationKey) {
            var text = Component.translatable(translationKey);
            return new State(color, () -> text);
        }
    }

    private final State[] states;
    private final SyncValue<Integer> state;

    protected Indicator(IntSupplier state, State... states) {
        this.states = states;
        layout(l -> l.size(SIZE, SIZE));
        this.state = addSyncValue(SyncValue.ofInt(state::getAsInt, 0).onChanged(this::applyTooltip));
        applyTooltip(0);
    }

    public static Indicator of(IntSupplier state, State... states) {
        return new Indicator(state, states);
    }

    private void applyTooltip(int index) {
        if (index >= 0 && index < states.length) setHoverTooltips(states[index].tooltip().get());
        else setHoverTooltips(new Component[0]);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getPositionX(), y = getPositionY();
        UITheme.INSET.draw(graphics, UIStates.NONE, x, y, SIZE, SIZE);
        int index = state.getValue();
        int color = index >= 0 && index < states.length ? states[index].color() : UNKNOWN_COLOR;
        UIDraw.insetLamp(graphics, UIPixels.center(x, SIZE, LAMP), UIPixels.center(y, SIZE, LAMP), LAMP, color);
    }
}
