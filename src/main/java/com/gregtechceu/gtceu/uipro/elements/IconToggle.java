package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 图标开关：方形图标按钮，点一下在"开 / 关"之间切换。开的时候整块用确认色（绿），与按钮组选中项一致；关的时候是普通按钮。
 * 用在只能放一个图标的地方（如三视图下方"允许从输出面输入"），有文字说明的地方用 {@link Switch} 或 {@link ButtonGroup#multi}。
 * <p>
 * 开关状态由服务端取值、经自己的同步值下发；点击只在服务端取反。悬停说明用 {@link #tooltips}（开 / 关各一条）或 {@code setHoverTooltips}。
 */
public class IconToggle extends Button {

    private final SyncValue<Boolean> on;
    private final BooleanSupplier getter;

    protected IconToggle(IGuiTexture icon, int size, BooleanSupplier getter, Consumer<Boolean> setter) {
        super(size, size, null, icon);
        this.getter = getter;
        this.on = addSyncValue(SyncValue.of(getter::getAsBoolean, SyncValue.BOOLEAN, false));
        setVariant(() -> on.getValue() ? UITheme.ButtonVariant.CONFIRM : UITheme.ButtonVariant.DEFAULT);
        setOnServerClick(() -> setter.accept(!getter.getAsBoolean()));
    }

    /** 与物品槽同尺寸（{@link UISizes#SLOT}）的图标开关。 */
    public static IconToggle of(IGuiTexture icon, BooleanSupplier getter, Consumer<Boolean> setter) {
        return new IconToggle(icon, UISizes.SLOT, getter, setter);
    }

    /** 开 / 关两种状态各一条说明（翻译键写全，不拼后缀）；由服务端按当前状态取值下发。 */
    public IconToggle tooltips(String onKey, String offKey) {
        bindTooltip(() -> Component.translatable(getter.getAsBoolean() ? onKey : offKey));
        return this;
    }

    public boolean isOn() {
        return on.getValue();
    }
}
