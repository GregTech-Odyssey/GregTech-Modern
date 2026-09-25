package com.gregtechceu.gtceu.uiwidgets.icon;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.uipro.styletemplate.WidgetIconAtlas;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

/**
 * 机器小组件（窗口左侧配置标签）的通用图标，画法标准见 {@link WidgetIconAtlas}。
 * 开关类成对给出 {@code _OFF}（灰）/ {@code _ON}（彩色），直接作 {@code IFancyConfiguratorButton.Toggle} 的两态。
 */
public final class WidgetIcons {

    /// 行数必须与图集高度一致；加图标时加一行并改这里
    private static final WidgetIconAtlas ATLAS = new WidgetIconAtlas(GTCEu.id("textures/gui/uiwidgets/widget_icons.png"), 34);

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

    public static final IGuiTexture COVER_IMPORT = ATLAS.pixelIcon(18, 0);
    public static final IGuiTexture COVER_EXPORT = ATLAS.pixelIcon(18, 1);
    public static final IGuiTexture DISTRIBUTION_ROUND_ROBIN = ATLAS.pixelIcon(19, 0);
    public static final IGuiTexture DISTRIBUTION_ROUND_ROBIN_PRIORITY = ATLAS.pixelIcon(19, 1);
    public static final IGuiTexture DISTRIBUTION_INSERT_FIRST = ATLAS.pixelIcon(19, 2);
    public static final IGuiTexture MANUAL_IO_DISABLED = ATLAS.pixelIcon(20, 0);
    public static final IGuiTexture MANUAL_IO_FILTERED = ATLAS.pixelIcon(20, 1);
    public static final IGuiTexture MANUAL_IO_UNFILTERED = ATLAS.pixelIcon(20, 2);
    public static final IGuiTexture BUCKET = ATLAS.pixelIcon(21, 0);
    public static final IGuiTexture MILLI_BUCKET = ATLAS.pixelIcon(21, 1);
    public static final IGuiTexture TRANSFER_ANY = ATLAS.pixelIcon(22, 0);
    public static final IGuiTexture TRANSFER_EXACT = ATLAS.pixelIcon(22, 1);
    public static final IGuiTexture KEEP_EXACT = ATLAS.pixelIcon(22, 2);
    public static final IGuiTexture VOID_ANY = ATLAS.pixelIcon(23, 0);
    public static final IGuiTexture VOID_OVERFLOW = ATLAS.pixelIcon(23, 1);
    public static final IGuiTexture FILTER_INSERT = ATLAS.pixelIcon(24, 0);
    public static final IGuiTexture FILTER_EXTRACT = ATLAS.pixelIcon(24, 1);
    public static final IGuiTexture FILTER_BOTH = ATLAS.pixelIcon(24, 2);
    public static final IGuiTexture ACCESS_PUBLIC = ATLAS.pixelIcon(25, 0);
    public static final IGuiTexture ACCESS_PRIVATE = ATLAS.pixelIcon(25, 1);
    public static final IGuiTexture FILTER_SLOT = ATLAS.pixelIcon(26);

    public static final IGuiTexture STATUS_INFO = new WidgetIconAtlas(GTCEu.id("textures/gui/uipro/status_icons.png"), 1).pixelIcon(0);
    public static final IGuiTexture IDLE_NO_RECIPE = ATLAS.pixelIcon(27, 0);
    public static final IGuiTexture IDLE_NO_POWER = ATLAS.pixelIcon(27, 1);
    public static final IGuiTexture IDLE_LOW_TIER = ATLAS.pixelIcon(27, 2);
    public static final IGuiTexture IDLE_NO_CAPABILITY = ATLAS.pixelIcon(27, 3);
    public static final IGuiTexture IDLE_CONDITION = ATLAS.pixelIcon(31, 0);
    public static final IGuiTexture IDLE_OVERHEAT = ATLAS.pixelIcon(31, 1);
    public static final IGuiTexture STATUS_MAINTENANCE = ATLAS.pixelIcon(31, 2);
    public static final IGuiTexture STATUS_OBSTRUCTED = ATLAS.pixelIcon(31, 3);
    public static final IGuiTexture IDLE_INPUT_SHORT = ATLAS.pixelIcon(32, 0);
    public static final IGuiTexture IDLE_OUTPUT_FULL = ATLAS.pixelIcon(32, 1);
    public static final IGuiTexture IDLE_NO_FUEL = ATLAS.pixelIcon(32, 2);
    public static final IGuiTexture IDLE_NO_MANA = ATLAS.pixelIcon(32, 3);
    public static final IGuiTexture IDLE_NO_COMPUTATION = ATLAS.pixelIcon(33, 0);
    public static final IGuiTexture IDLE_NO_KINETIC = ATLAS.pixelIcon(33, 1);
    public static final IGuiTexture IDLE_LOW_TEMPERATURE = ATLAS.pixelIcon(33, 2);
    public static final IGuiTexture IDLE_WAITING = ATLAS.pixelIcon(33, 3);
    public static final IGuiTexture ALLOW_INPUT_ITEM = ATLAS.pixelIcon(28, 0);
    public static final IGuiTexture ALLOW_INPUT_FLUID = ATLAS.pixelIcon(28, 1);
    public static final IGuiTexture COVER_SETTINGS = ATLAS.pixelIcon(29);
    public static final IGuiTexture COVER_SLOT = ATLAS.pixelIcon(30);

    private WidgetIcons() {}
}
