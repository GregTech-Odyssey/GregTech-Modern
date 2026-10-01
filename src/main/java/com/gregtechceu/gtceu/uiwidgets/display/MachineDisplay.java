package com.gregtechceu.gtceu.uiwidgets.display;

import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.RichText;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.TextPane;
import com.gregtechceu.gtceu.uipro.issue.MachineDiagnosisTab;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uiwidgets.multiblock.ControlPanel;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;

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
 * <li>不再重复机器名：窗口标题栏已经有；</li>
 * <li>标准尺寸：宽 {@link UISizes#CONTENT_WIDTH}（与玩家背包对齐）、高 {@link UISizes#MACHINE_PAGE_HEIGHT}，与三视图设置页相同；
 * 长句自动换行，放不下时滚动，右下角可以拖高（所有多方块共用一个锁定高度）。</li>
 * </ul>
 * 数据流不变：服务端取文字、变化时下发，附加数据经 {@code readClientTextData / writeClientTextData}，可点击片段交给 {@code handleDisplayClick}。
 */
public final class MachineDisplay {

    /** 显示窗滚动区的 id：所有机器共用，玩家锁定的高度对所有多方块生效。 */
    public static final String SCROLLER_ID = "machine.display";
    public static final int DETAILS_HEIGHT = UISizes.MACHINE_PAGE_HEIGHT + UISizes.PLAYER_INVENTORY_HEIGHT;

    private MachineDisplay() {}

    /** 多方块控制器的主页：一个显示窗。需要在显示窗下方加东西的机器可以往返回的纵向容器里继续加。 */
    public static UIElement page(IDisplayUIMachine machine) {
        return column().addChild(display(machine));
    }

    public static UIElement detailsPage(IDisplayUIMachine machine) {
        return column().addChild(display(machine, DETAILS_HEIGHT));
    }

    /** 同样写法的其他机器：{@code text} 只在服务端调用，{@code click} 可为 null。 */
    public static UIElement page(MetaMachine machine, Consumer<List<Component>> text, @Nullable BiConsumer<String, ClickData> click) {
        return page(machine, text, click, UISizes.MACHINE_PAGE_HEIGHT);
    }

    public static UIElement page(MetaMachine machine, Consumer<List<Component>> text, @Nullable BiConsumer<String, ClickData> click, int height) {
        return column().addChild(display(machine, text, click, height));
    }

    public static UIElement page(MetaMachine machine, Consumer<List<Component>> text) {
        return page(machine, text, (BiConsumer<String, ClickData>) null);
    }

    public static UIElement page(MetaMachine machine, Consumer<List<Component>> text, Consumer<ControlPanel> controls) {
        var panel = ControlPanel.of(machine);
        controls.accept(panel);
        var page = page(machine, text);
        if (!panel.isEmpty()) page.addChild(panel.build());
        return page;
    }

    /** 与其他页面同宽、区块间距 {@link UISizes#SECTION_GAP} 的纵向容器。 */
    public static UIElement column() {
        return UIElement.column(UISizes.CONTENT_WIDTH).layout(l -> l.gapAll(UISizes.SECTION_GAP));
    }

    public static ScrollerView display(IDisplayUIMachine machine) {
        return display(machine, UISizes.MACHINE_PAGE_HEIGHT);
    }

    public static ScrollerView display(IDisplayUIMachine machine, int height) {
        var text = new RichText();
        text.setTextData(machine::writeClientTextData, machine::readClientTextData);
        text.textSupplier(machine.self().isRemote() ? null : machine::addDisplayText).clickHandler(machine::handleDisplayClick);
        return wrap(text, height);
    }

    public static ScrollerView display(MetaMachine machine, Consumer<List<Component>> text, @Nullable BiConsumer<String, ClickData> click) {
        return display(machine, text, click, UISizes.MACHINE_PAGE_HEIGHT);
    }

    public static ScrollerView display(MetaMachine machine, Consumer<List<Component>> text, @Nullable BiConsumer<String, ClickData> click, int height) {
        var richText = new RichText();
        richText.textSupplier(machine.isRemote() ? null : text);
        if (click != null) richText.clickHandler(click);
        return wrap(richText, height);
    }

    private static ScrollerView wrap(RichText text, int height) {
        return TextPane.screen(SCROLLER_ID, UISizes.CONTENT_WIDTH, height, text).fitPage();
    }

    /**
     * 只有显示窗的机器（没有实现 {@code IFancyUIMachine} 的 {@link IDisplayUIMachine}，如蒸汽多方块）用的页面提供者：
     * 交给 {@code MachineWindow} 作主页，标题是方块名、图标是机器物品。
     */
    public static IFancyUIProvider provider(IDisplayUIMachine machine) {
        return new IFancyUIProvider() {

            @Override
            public UIElement createMainPage(FancyMachineUIWidget widget) {
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

            @Override
            public boolean showsWindowLogo() {
                return false;
            }

            @Override
            public MetaMachine getIssueMachine() {
                return machine.self();
            }

            @Override
            public void attachSideTabs(TabsWidget sideTabs) {
                sideTabs.setMainTab(this);
                MachineDiagnosisTab.attachIfEnabled(sideTabs, machine);
            }

            @Override
            public void attachTooltips(TooltipsPanel tooltipsPanel) {
                tooltipsPanel.attachRecipeLogics(machine.self());
            }
        };
    }
}
