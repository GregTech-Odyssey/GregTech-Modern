package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * 原位二击确认按钮：第一次点击进入待确认（换文字、换危险色），限时内再点一次才在服务端执行，超时自动回落；
 * 待确认状态由服务端持有并下发，每个打开的界面各自独立。
 */
public class ConfirmButton extends Button {

    public static final String ARMED = "gtceu.uipro.confirm.armed";
    public static final int CONFIRM_TICKS = 60;

    private final Component idleText;
    private final Component armedText;
    private final SyncValue<Boolean> armed;
    private UITheme.ButtonVariant idleVariant = UITheme.ButtonVariant.DANGER;
    @Nullable
    private Runnable onServerConfirm;
    private long armedUntil = Long.MIN_VALUE;

    protected ConfirmButton(int width, Component idleText, Component armedText) {
        super(width, HEIGHT, null, null);
        this.idleText = idleText;
        this.armedText = armedText;
        this.armed = addSyncValue(SyncValue.ofBool(this::isServerArmed));
        bindClientVariant(() -> armed.getValue() ? UITheme.ButtonVariant.DANGER : idleVariant);
        setOnServerClick(this::serverClick);
    }

    public static ConfirmButton translatable(int width, String key) {
        return translatable(width, key, ARMED);
    }

    public static ConfirmButton translatable(int width, String key, String armedKey) {
        return new ConfirmButton(width, Component.translatable(key), Component.translatable(armedKey));
    }

    public ConfirmButton setOnServerConfirm(Runnable onServerConfirm) {
        this.onServerConfirm = onServerConfirm;
        return this;
    }

    @Override
    public ConfirmButton setVariant(UITheme.ButtonVariant variant) {
        this.idleVariant = variant;
        return this;
    }

    public boolean isArmed() {
        return armed.getValue();
    }

    @Override
    protected Component getDisplayText() {
        return armed.getValue() ? armedText : idleText;
    }

    private long serverTime() {
        var gui = getGui();
        return gui == null || gui.entityPlayer == null ? Long.MIN_VALUE : gui.entityPlayer.level().getGameTime();
    }

    private boolean isServerArmed() {
        long now = serverTime();
        return now != Long.MIN_VALUE && now < armedUntil;
    }

    private void serverClick() {
        long now = serverTime();
        if (now == Long.MIN_VALUE) return;
        if (now < armedUntil) {
            armedUntil = Long.MIN_VALUE;
            if (onServerConfirm != null) onServerConfirm.run();
        } else {
            armedUntil = now + CONFIRM_TICKS;
        }
    }
}
