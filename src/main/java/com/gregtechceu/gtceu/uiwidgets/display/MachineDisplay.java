package com.gregtechceu.gtceu.uiwidgets.display;

import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.RichText;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 机器状态显示窗：多方块控制器（以及沿用同样写法的单方块机器）逐行拼出的状态文字（{@code addDisplayText}）。
 * <p>
 * 取代 GTM 的深色显示屏（{@code GuiTextures.DISPLAY} + 机器名 + {@code ComponentPanelWidget}）：
 * <ul>
 * <li>外观是框架的状态显示窗（{@link UITheme#STATUS_PANEL}，浅色下凹），文字颜色按亮底重配（{@link RichText}）；</li>
 * <li>不再重复机器名：窗口标题栏已经有；</li>
 * <li>标准尺寸：宽 {@link UISizes#CONTENT_WIDTH}（与玩家背包对齐）、高 {@link UISizes#MACHINE_PAGE_HEIGHT}，与三视图设置页相同；
 * 长句自动换行，放不下时滚动，右下角可以拖高（所有多方块共用一个锁定高度）。</li>
 * </ul>
 * 数据流不变：服务端取文字、变化时下发，附加数据经 {@code readClientTextData / writeClientTextData}，可点击片段交给 {@code handleDisplayClick}。
 */
public final class MachineDisplay {

    /** 显示窗滚动区的 id：所有机器共用，玩家锁定的高度对所有多方块生效。 */
    public static final String SCROLLER_ID = "machine.display";

    private MachineDisplay() {}

    /** 多方块控制器的主页：一个显示窗。需要在显示窗下方加东西的机器可以往返回的纵向容器里继续加。 */
    public static UIElement page(IDisplayUIMachine machine) {
        return column().addChild(display(machine));
    }

    /** 同样写法的其他机器：{@code text} 只在服务端调用，{@code click} 可为 null。 */
    public static UIElement page(MetaMachine machine, Consumer<List<Component>> text, @Nullable BiConsumer<String, ClickData> click) {
        return column().addChild(display(machine, text, click));
    }

    /** 与其他页面同宽、区块间距 {@link UISizes#SECTION_GAP} 的纵向容器。 */
    public static UIElement column() {
        return UIElement.column(UISizes.CONTENT_WIDTH).layout(l -> l.gapAll(UISizes.SECTION_GAP));
    }

    public static ScrollerView display(IDisplayUIMachine machine) {
        var text = new RichText();
        text.setTextDataReader(machine::readClientTextData).setTextDataWriter(machine::writeClientTextData);
        text.textSupplier(machine.self().isRemote() ? null : machine::addDisplayText).clickHandler(machine::handleDisplayClick);
        return wrap(text);
    }

    public static ScrollerView display(MetaMachine machine, Consumer<List<Component>> text, @Nullable BiConsumer<String, ClickData> click) {
        var richText = new RichText();
        richText.textSupplier(machine.isRemote() ? null : text);
        if (click != null) richText.clickHandler(click);
        return wrap(richText);
    }

    private static ScrollerView wrap(RichText text) {
        var scroller = new ScrollerView(SCROLLER_ID, UISizes.CONTENT_WIDTH, UISizes.MACHINE_PAGE_HEIGHT)
                .layoutContent(l -> l.paddingAll(UITheme.PANEL_PADDING));
        scroller.setBackground(UITheme.STATUS_PANEL);
        scroller.addScrollViewChild(text);
        return scroller;
    }

    /**
     * 只有显示窗的机器（没有实现 {@code IFancyUIMachine} 的 {@link IDisplayUIMachine}，如蒸汽多方块）用的页面提供者：
     * 交给 {@code MachineWindow} 作主页，标题是方块名、图标是机器物品。
     */
    public static IFancyUIProvider provider(IDisplayUIMachine machine) {
        return new IFancyUIProvider() {

            @Override
            public Widget createMainPage(FancyMachineUIWidget widget) {
                return page(machine);
            }

            @Override
            public IGuiTexture getTabIcon() {
                return new ItemStackTexture(machine.self().getDefinition().asStack());
            }

            @Override
            public Component getTitle() {
                return machine.self().getBlockState().getBlock().getName();
            }
        };
    }
}
