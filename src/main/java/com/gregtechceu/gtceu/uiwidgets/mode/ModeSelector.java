package com.gregtechceu.gtceu.uiwidgets.mode;

import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ButtonGroup;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * 机器模式（配方类型）选择页：一个区块，标题下是单选按钮组（{@link ButtonGroup}），每个模式一行，当前模式点亮；点一下切换。
 * 
 * <pre>
 *  ┌ 机器模式 ─────────────────┐
 *  │ [ 模式 A              ]  │  ← 当前模式：绿色、标记点亮
 *  │ [ 模式 B              ]  │
 *  └──────────────────────────┘
 * </pre>
 * 
 * 当前模式由服务端判定、经按钮组的同步值下发，客户端只用来绘制（不回写机器，避免客户端触发切换的副作用）。
 * 模式个数与名称两端建页时各取一次，必须两端一致（控件树一致）。
 */
public final class ModeSelector {

    public static final String TITLE = "gtceu.gui.machinemode.title";

    private ModeSelector() {}

    /**
     * @param count   模式个数（两端一致）
     * @param name    第 i 个模式的名称（两端都会调用，只能用两端都有的数据，如配方类型的翻译键）
     * @param current 服务端：当前模式序号
     * @param select  服务端：切换到第 i 个模式
     */
    public static UIElement create(int count, IntFunction<Component> name, IntSupplier current, IntConsumer select) {
        var section = UIElement.section(UISizes.CONTENT_WIDTH);
        section.addChild(TextLine.translatable(LayoutStyle.AUTO, TITLE).setColor(UITheme.PANEL_TEXT));
        return section.addChild(ButtonGroup.single(count, name, current, select));
    }
}
