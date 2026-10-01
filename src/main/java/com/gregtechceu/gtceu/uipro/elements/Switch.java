package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.data.Binding;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.render.UIStates;
import com.gregtechceu.gtceu.uipro.styletemplate.OreSprites;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;

import java.util.function.BooleanSupplier;

/**
 * Ore UI 开关，对应 LDLib2 {@code Switch}：开为绿色"I"、关为深色"O"（{@link OreSprites#SWITCH_ON}/{@link OreSprites#SWITCH_OFF}）。
 * <p>
 * 标准尺寸 {@link #WIDTH} × {@link #HEIGHT}，即贴图原尺寸。状态以服务端为准下发。
 * 禁用（{@link #disabled}，或上级元素禁用）时与其他控件一样叠统一斜纹、点击无效、悬停先"禁止操作"再原因（见 {@link com.gregtechceu.gtceu.uipro.ElementState}）。
 */
public final class Switch extends Button {

    public static final int WIDTH = UISizes.SWITCH_WIDTH;
    public static final int HEIGHT = UISizes.CONTROL_HEIGHT;

    /// 开关贴图（{@link OreSprites#SWITCH_ON}/{@link OreSprites#SWITCH_OFF}）本体从第 4 行开始，上面是透明留白
    private static final int BODY_TOP = 4;

    private final Binding<Boolean> on;

    private Switch(BooleanSupplier getter, BooleanConsumer setter) {
        super(WIDTH, HEIGHT, null, null);
        this.on = addBinding(Binding.bindBool(getter, setter));
        setOnClientClick(() -> on.set(!on.getValue()));
    }

    public static Switch of(BooleanSupplier getter, BooleanConsumer setter) {
        return new Switch(getter, setter);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int states = UIStates.with(ElementState.resolve(this, mouseX, mouseY), UIStates.CHECKED, on.getValue());
        boolean disabled = UIStates.has(states, UIStates.DISABLED);
        UITheme.SWITCH.draw(graphics, states, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        // 开关贴图上方 BODY_TOP 行是透明的，斜纹只画在本体上，不能盖到上方的面板底色
        if (disabled) UIDraw.disabledHatch(graphics, getPositionX(), getPositionY() + BODY_TOP, getSizeWidth(), getSizeHeight() - BODY_TOP);
    }
}
