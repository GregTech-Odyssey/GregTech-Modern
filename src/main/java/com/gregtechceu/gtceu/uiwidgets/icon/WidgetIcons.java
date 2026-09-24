package com.gregtechceu.gtceu.uiwidgets.icon;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.uipro.styletemplate.WidgetIconAtlas;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

/**
 * 机器小组件（窗口左侧配置标签）的通用图标，画法标准见 {@link WidgetIconAtlas}。
 * 开关类成对给出 {@code _OFF}（灰）/ {@code _ON}（彩色），直接作 {@code IFancyConfiguratorButton.Toggle} 的两态。
 */
public final class WidgetIcons {

    /// 行数必须与图集高度一致（64×288 → 18 行）；加图标时加一行并改这里
    private static final WidgetIconAtlas ATLAS = new WidgetIconAtlas(GTCEu.id("textures/gui/uiwidgets/widget_icons.png"), 18);

    /// 机器开关
    public static final IGuiTexture POWER_OFF = ATLAS.icon(0, 0);
    public static final IGuiTexture POWER_ON = ATLAS.icon(0, 1);

    /// 销毁模式：不销毁 / 物品 / 流体 / 全部（与 {@code VoidingMode} 顺序一致）
    public static IGuiTexture voiding(int mode) {
        return ATLAS.icon(1, mode);
    }

    /// 输入限制（锁）
    public static final IGuiTexture INPUT_LIMIT_OFF = ATLAS.icon(2, 0);
    public static final IGuiTexture INPUT_LIMIT_ON = ATLAS.icon(2, 1);
    /// 未放编程电路
    public static final IGuiTexture CIRCUIT_NONE = ATLAS.icon(3);
    /// 全部退回
    public static final IGuiTexture REFUND = ATLAS.icon(4);
    /// ME 自动拉取
    public static final IGuiTexture AUTO_PULL_OFF = ATLAS.icon(5, 0);
    public static final IGuiTexture AUTO_PULL_ON = ATLAS.icon(5, 1);
    /// 物品库（箱子）
    public static final IGuiTexture ITEMS = ATLAS.icon(6);
    /// 流体库（液滴）
    public static final IGuiTexture FLUIDS = ATLAS.icon(7);
    /// 总线隔离
    public static final IGuiTexture DISTINCT_OFF = ATLAS.icon(8, 0);
    public static final IGuiTexture DISTINCT_ON = ATLAS.icon(8, 1);
    /// 批处理
    public static final IGuiTexture BATCH_OFF = ATLAS.icon(9, 0);
    public static final IGuiTexture BATCH_ON = ATLAS.icon(9, 1);
    /// 高亮显示（一次性动作）
    public static final IGuiTexture HIGHLIGHT = ATLAS.icon(10);
    /// 设置（齿轮）
    public static final IGuiTexture SETTINGS = ATLAS.icon(11);
    /// 说明
    public static final IGuiTexture INFO = ATLAS.icon(12);
    /// 过滤设置（漏斗）
    public static final IGuiTexture FILTER = ATLAS.icon(13);
    /// 黑名单 / 白名单
    public static final IGuiTexture BLACKLIST = ATLAS.icon(14, 0);
    public static final IGuiTexture WHITELIST = ATLAS.icon(14, 1);
    /// 清空
    public static final IGuiTexture CLEAR = ATLAS.icon(15);
    /// 能量（闪电）
    public static final IGuiTexture ENERGY_OFF = ATLAS.icon(16, 0);
    public static final IGuiTexture ENERGY_ON = ATLAS.icon(16, 1);

    /// 输入 / 输出方向（箭头进盒 / 出盒）
    public static final IGuiTexture IMPORT = ATLAS.icon(17, 0);
    public static final IGuiTexture EXPORT = ATLAS.icon(17, 1);

    private WidgetIcons() {}
}
