package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class PageSwitcher implements IFancyUIProvider {

    private final Consumer<IFancyUIProvider> onPageSwitched;

    private List<IFancyUIProvider> pages = Collections.emptyList();
    private IFancyUIProvider currentPage = null;

    public PageSwitcher(Consumer<IFancyUIProvider> onPageSwitched) {
        this.onPageSwitched = onPageSwitched;
    }

    public void setPageList(List<IFancyUIProvider> allPages, IFancyUIProvider currentPage) {
        this.pages = allPages;
        this.currentPage = currentPage;
    }

    /// 页面按钮边长：按钮面 20，16 的图标居中
    private static final int PAGE_BUTTON = 22;
    /// 每行页面按钮数（区块内容宽 156 放得下 6 个）
    private static final int PAGES_PER_ROW = 6;
    /// 页面列表视口最高多少，再多就滚动
    private static final int MAX_LIST_HEIGHT = 146;

    /**
     * 页面切换页（新式框架组件）：按分组各一个区块，组名在上，下面每行 {@link #PAGES_PER_ROW} 个页面图标按钮，
     * 悬停显示页面名；页面多时列表滚动。点击两端各切换一次（界面结构两端同步变化）。
     */
    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        var list = new ScrollerView("page_switcher", UISizes.CONTENT_WIDTH, PAGE_BUTTON, UISizes.SECTION_GAP)
                .adaptiveHeight(MAX_LIST_HEIGHT);
        var groupedPages = pages.stream().collect(Collectors.groupingBy(
                page -> Objects.requireNonNullElse(page.getPageGroupingData(), new PageGroupingData(null, -1))));
        groupedPages.keySet().stream()
                .sorted(Comparator.comparingInt(PageGroupingData::groupPositionWeight))
                .forEachOrdered(group -> {
                    var section = UIElement.section();
                    if (group.groupKey() != null) {
                        section.addChild(TextLine.translatable(LayoutStyle.AUTO, group.groupKey()).setColor(UITheme.PANEL_TEXT));
                    }
                    var groupPages = groupedPages.get(group);
                    for (int rowStart = 0; rowStart < groupPages.size(); rowStart += PAGES_PER_ROW) {
                        var row = UIElement.row(PAGE_BUTTON).layout(l -> l.gapAll(UISizes.GAP));
                        for (int i = rowStart; i < Math.min(groupPages.size(), rowStart + PAGES_PER_ROW); i++) {
                            var page = groupPages.get(i);
                            var button = Button.icon(page.getTabIcon(), PAGE_BUTTON).setOnClick(clickData -> onPageSwitched.accept(page));
                            button.setHoverTooltips(page.getTitle());
                            row.addChild(button);
                        }
                        section.addChild(row);
                    }
                    list.addScrollViewChild(section);
                });
        return UIElement.column(UISizes.CONTENT_WIDTH).addChild(list);
    }

    /// 与标题栏打开本页的按钮同一个图标（原先是 GTM 的"+"字符，放在标题栏里像一个多余的加号）
    @Override
    public IGuiTexture getTabIcon() {
        return UITheme.PAGES;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtceu.gui.title_bar.page_switcher");
    }

    @Override
    public boolean hasPlayerInventory() {
        return false;
    }
}
