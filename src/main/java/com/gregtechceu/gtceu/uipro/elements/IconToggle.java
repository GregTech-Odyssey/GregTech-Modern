package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * 图标开关：方形图标按钮，点一下在"开 / 关"之间切换。开的时候整块用确认色（绿），与按钮组选中项一致；关的时候是普通按钮。
 * 用在只能放一个图标的地方（如三视图下方"允许从输出面输入"），有文字说明的地方用 {@link Switch} 或 {@link ButtonGroup#multi}。
 * <p>
 * 开关状态由服务端取值、经自己的同步值下发；点击只在服务端取反。
 */
public class IconToggle extends Button {

    private final SyncValue<Boolean> on;
    private final BooleanSupplier getter;

    protected IconToggle(int size, IGuiTexture icon, BooleanSupplier getter, BooleanConsumer setter) {
        super(size, size, null, icon);
        this.getter = getter;
        this.on = addSyncValue(SyncValue.ofBool(getter));
        bindClientVariant(() -> on.getValue() ? UITheme.ButtonVariant.CONFIRM : UITheme.ButtonVariant.DEFAULT);
        setOnServerClick(() -> setter.accept(!getter.getAsBoolean()));
    }

    public static IconToggle of(IGuiTexture icon, BooleanSupplier getter, BooleanConsumer setter) {
        return new IconToggle(UISizes.SLOT_SIZE, icon, getter, setter);
    }

    public IconToggle onOffTooltips(String onKey, String offKey) {
        return (IconToggle) bindTooltip(() -> Component.translatable(getter.getAsBoolean() ? onKey : offKey));
    }

    public IconToggle onOffTooltips(Function<Boolean, List<Component>> tooltips) {
        on.onChanged(value -> setHoverTooltips(tooltips.apply(value)));
        setHoverTooltips(tooltips.apply(on.getValue()));
        return this;
    }

    public boolean isOn() {
        return on.getValue();
    }
}
