package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.styletemplate.OreSprites;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Ore UI 开关，对应 LDLib2 {@code Switch}：开为绿色"I"、关为深色"O"（{@link OreSprites#SWITCH_ON}/{@link OreSprites#SWITCH_OFF}）。
 * <p>
 * 标准尺寸 {@link #WIDTH} × {@link #HEIGHT}，即贴图原尺寸。状态以服务端为准下发；点击在服务端取反后写入 {@code setter}。
 * 禁用（{@link #disabled}，或上级元素禁用）时与其他控件一样叠统一斜纹、点击无效、悬停先"禁止操作"再原因（见 {@link com.gregtechceu.gtceu.uipro.ElementState}）。
 */
public final class Switch extends Button {

    public static final int WIDTH = UISizes.SWITCH_WIDTH;
    public static final int HEIGHT = UISizes.CONTROL_HEIGHT;

    /// 开关贴图（{@link OreSprites#SWITCH_ON}/{@link OreSprites#SWITCH_OFF}）本体从第 4 行开始，上面是透明留白
    private static final int BODY_TOP = 4;

    private final SyncValue<Boolean> on;

    private Switch(BooleanSupplier getter, BooleanConsumer setter) {
        super(WIDTH, HEIGHT, null, null);
        this.on = addSyncValue(SyncValue.of(getter::getAsBoolean, SyncValue.BOOLEAN, getter.getAsBoolean()));
        setOnServerClick(() -> setter.accept(!getter.getAsBoolean()));
    }

    public static Switch of(BooleanSupplier getter, BooleanConsumer setter) {
        return new Switch(getter, setter);
    }

    @Override
    public Switch disabled(BooleanSupplier serverCondition, @Nullable String reasonKey) {
        super.disabled(serverCondition, reasonKey);
        return this;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        boolean disabled = isDisabled();
        var sprite = on.getValue() ? OreSprites.SWITCH_ON : OreSprites.SWITCH_OFF;
        if (!disabled && isMouseOverElement(mouseX, mouseY)) sprite = sprite.tinted(OreSprites.PRESSED_TINT);
        sprite.draw(graphics, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        // 开关贴图上方 BODY_TOP 行是透明的，斜纹只画在本体上，不能盖到上方的面板底色
        if (disabled) UITheme.drawDisabled(graphics, getPositionX(), getPositionY() + BODY_TOP, getSizeWidth(), getSizeHeight() - BODY_TOP);
    }
}
