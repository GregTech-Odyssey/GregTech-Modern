package com.gregtechceu.gtceu.api.gui.widget;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.common.data.GTItems;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 在左侧配置面板里展开某一面覆盖板的设置界面（{@link IUICover#createUIWidget()}）。
 * <p>
 * 覆盖板界面自带标题（见 {@link #COVER_TITLE_HEIGHT}），所以本配置项的面板标题留空，由覆盖板自带的标题填进面板的标题行；
 * 否则面板标题行下面还会再出现一行覆盖板标题（两行标题）。配置按钮的悬停说明仍是"覆盖板设置"。
 */
public class CoverConfigurator implements IFancyConfigurator {

    /**
     * 覆盖板界面的排布约定：最上面这么高的一条是它自带的标题行（GTM 各覆盖板在 y = 5 画标题，内容从 y = 20 左右开始）。
     * 在面板里展开时整个界面上移这么多，标题落进面板的标题行（与右侧图标同一行），外框高度也扣掉这一截。
     * 自己实现 {@link IUICover#createUIWidget()} 的覆盖板要遵守：顶部留出这一行放标题，否则内容会被推进标题行。
     */
    public static final int COVER_TITLE_HEIGHT = 20;
    /// 上移时同时左移的量（沿用 GTM 原值）
    private static final int COVER_OFFSET_X = 1;

    protected final ICoverable coverable;
    // runtime
    @Nullable
    protected final Direction side;
    @Nullable
    protected final CoverBehavior coverBehavior;

    /** 留空：标题由覆盖板界面自带（见类说明）。 */
    @Override
    public Component getTitle() {
        return Component.empty();
    }

    @Override
    public List<Component> getTooltips() {
        return List.of(Component.translatable("gtceu.gui.cover_setting.title"));
    }

    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(GTItems.ITEM_FILTER.get());
    }

    /**
     * 展开后的内容：覆盖板界面整体上移 {@link #COVER_TITLE_HEIGHT}，外框尺寸是覆盖板界面去掉标题行的部分，不再写死最小尺寸。
     * 底图、四周内边距由左侧面板统一处理。该面没有带界面的覆盖板时是空的外框。
     */
    @Override
    public Widget createConfigurator() {
        WidgetGroup group = new WidgetGroup(0, 0, 0, 0);
        if (side == null || !(coverable.getCoverAtSide(side) instanceof IUICover iuiCover)) return group;

        Widget cover = iuiCover.createUIWidget();
        cover.addSelfPosition(-COVER_OFFSET_X, -COVER_TITLE_HEIGHT);
        group.addWidget(cover);
        group.setSize(new Size(cover.getSizeWidth(), Math.max(0, cover.getSizeHeight() - COVER_TITLE_HEIGHT)));
        return group;
    }

    public CoverConfigurator(final ICoverable coverable, @Nullable final Direction side, @Nullable final CoverBehavior coverBehavior) {
        this.coverable = coverable;
        this.side = side;
        this.coverBehavior = coverBehavior;
    }
}
