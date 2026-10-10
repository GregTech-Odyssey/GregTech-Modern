package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.UIStructure;

import com.gto.datasynclib.datastream.codec.ByteBufCodecs;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 分页视图：同一时间只构建当前页，页码是这个界面的结构状态（服务端权威，不进机器字段）。
 */
public class PageView extends UIElement {

    private final List<Consumer<UIElement>> pages = new ArrayList<>();
    private final UIStructure<Integer> pageState;
    private int page = -1;

    public PageView(int width, int height) {
        layout(l -> l.column().size(width, height));
        pageState = addStructure(ByteBufCodecs.INT, () -> page)
                .validate(index -> index >= 0 && index < pages.size())
                .apply(this::show);
    }

    public PageView addPage(Consumer<UIElement> page) {
        pages.add(page);
        return this;
    }

    public int getPageCount() {
        return pages.size();
    }

    public int getPage() {
        return Math.max(0, page);
    }

    public int getTargetPage() {
        return Math.max(0, pageState.getTarget());
    }

    public void selectPage(int index) {
        if (pages.isEmpty()) return;
        pageState.request(Math.max(0, Math.min(index, pages.size() - 1)));
    }

    @Override
    public void initWidget() {
        if (page < 0 && !pages.isEmpty()) show(0);
        super.initWidget();
    }

    private void show(int index) {
        page = index;
        clearAllWidgets();
        if (index < 0 || index >= pages.size()) return;
        var content = UIElement.column(getContentWidth());
        pages.get(index).accept(content);
        addWidget(content);
    }
}
