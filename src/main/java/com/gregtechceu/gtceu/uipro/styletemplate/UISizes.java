package com.gregtechceu.gtceu.uipro.styletemplate;

import org.jetbrains.annotations.ApiStatus;

/**
 * 界面元素的标准尺寸。所有元素默认按这里的高度/宽度出图，同一行的元素因此能上下对齐、列宽能凑整。
 * <p>
 * 横向网格照原版容器：窗口宽 {@link #WINDOW_WIDTH}，左右内边距 {@link #WINDOW_PADDING_X}，
 * 内容宽 {@link #CONTENT_WIDTH} 正好 9 个槽——标题栏、页面里的每一行、玩家背包共用同一条左边缘。
 * <p>
 * 竖向节奏：一行控件 14 高（按钮、输入框、步进器、开关同高，Ore 开关贴图也是 14 高），一行物品/流体槽 18 高，
 * 文字行 9 高；元素间距 {@link #GAP}，区块之间 {@link #SECTION_GAP}，只有这两档。
 */
public final class UISizes {

    private UISizes() {}

    // ==================== 槽位 ====================
    /** 物品/流体槽边长。 */
    public static final int SLOT_SIZE = 18;
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "27.0")
    public static final int SLOT = SLOT_SIZE;
    /** 一行槽位数。 */
    public static final int SLOTS_PER_ROW = 9;
    /** 一整行槽位的宽度（162）。 */
    public static final int SLOT_ROW_WIDTH = SLOT_SIZE * SLOTS_PER_ROW;

    // ==================== 控件高度 ====================
    /** 标准控件行高：按钮、输入框、步进器、开关。 */
    public static final int CONTROL_HEIGHT = 14;
    /** 单行文字高度（字体行高 9）。 */
    public static final int TEXT_HEIGHT = 9;
    /** 窗口（整个界面）最多占屏幕（GUI 缩放后）的比例：列表自动加高、拖拽缩放都以此为上限。 */
    public static final float MAX_WINDOW_SCREEN_RATIO = 2f / 3f;
    /** 状态行高度：一行文字上下各留 1 像素（与单行 Label 同高），状态面板里逐行紧排。 */
    public static final int STATUS_LINE_HEIGHT = TEXT_HEIGHT + 2;
    /** 带物品图标的状态行高：放得下 16 像素的图标，上下各留 1。 */
    public static final int STATUS_LINE_ICON_HEIGHT = 18;
    /** 进度条高度：一行文字加上下各 1 像素轨道边与 1 像素留白（{@code ProgressBar}）。 */
    public static final int PROGRESS_BAR_HEIGHT = TEXT_HEIGHT + 3;

    // ==================== 控件宽度 ====================
    /** 方形图标按钮 / 步进器箭头的宽度，与控件行高相同。 */
    public static final int ICON_BUTTON_SIZE = CONTROL_HEIGHT;
    /**
     * 常规文字按钮宽度：四个汉字（36）加两侧留白。按内容宽 162 凑整——
     * 一行放下"步进器（VALUE_WIDTH 时 60）+ 两个按钮"：60 + 2 × 48 + 3 × 2 = 162。
     */
    public static final int BUTTON_WIDTH = 48;
    /** 开关宽度（Ore 开关贴图原宽）。 */
    public static final int SWITCH_WIDTH = 24;
    /** 数字显示框的最小宽度（步进器中间那格，放得下 "10/10"）。 */
    public static final int VALUE_WIDTH = 28;
    public static final int INLINE_ADJUSTER_WIDTH = 104;
    public static final int STEP_BUTTON_WIDTH = 38;

    // ==================== 间距 ====================
    /** 同一组内元素之间。 */
    public static final int GAP = 2;
    /** 区块与区块之间。 */
    public static final int SECTION_GAP = 4;
    /** 按钮文字两侧留白。 */
    public static final int TEXT_PADDING = 4;
    /** 区块内边距：让开 1 像素细边后留 2 像素空白。 */
    public static final int PANEL_PADDING = 3;
    public static final int PANEL_PADDING_BOTTOM = 3;
    /** 按钮的底部台阶高度：文字/图标要画在台阶以上的区域。 */
    public static final int BUTTON_LIP_HEIGHT = 2;
    /** 选项标记（单选圆点 / 多选勾选框）的边长。 */
    public static final int OPTION_MARK_SIZE = 7;
    /** 拖拽缩放角的边长（滚动区右下角，见 {@code ScrollerView}）。 */
    public static final int RESIZE_GRIP_SIZE = 6;
    public static final int DOCK_PADDING = 3;
    /// 弹出面板指向所属对象的小尖角高度
    public static final int POPUP_NOTCH_HEIGHT = 4;
    public static final int CONFIGURATOR_TAB_PRESS_DEPTH = 2;
    public static final int CONFIGURATOR_TAB_LATCH_DEPTH = 1;
    public static final int LOGO_WIDTH = 33;
    public static final int LOGO_HEIGHT = 8;
    public static final int LOGO_GAP = 2;
    public static final int VIEW_ICON_SIZE = 16;

    /**
     * 两端都能算的文字宽度估算（按字数，偏大不偏小）：中日韩文字 9 像素，其余 6 像素（原版字体最宽的 ASCII 含字距为 6）。
     * 用于按文字定宽的元素（如紧凑按钮组）：客户端的字体在服务端不存在。两端语言不同时算出的宽度可以不同，控件树不变。
     */
    public static int widthFor(String text) {
        int width = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            width += codePoint >= 0x2E80 ? 9 : 6;
            i += Character.charCount(codePoint);
        }
        return width;
    }

    // ==================== 窗口 ====================
    /** 机器窗口的标准宽度（原版容器宽）。 */
    public static final int WINDOW_WIDTH = 176;
    /** 窗口左右内边距：内容区从 x = 7 开始，与原版容器的槽位对齐。 */
    public static final int WINDOW_PADDING_X = 7;
    /** 窗口上内边距。 */
    public static final int WINDOW_PADDING_TOP = 5;
    /** 窗口下内边距：比上边多 2，抵掉 Ore 面板底部的厚边。 */
    public static final int WINDOW_PADDING_BOTTOM = 7;
    /** 窗口内容宽度（162，正好 9 个槽）。 */
    public static final int CONTENT_WIDTH = WINDOW_WIDTH - 2 * WINDOW_PADDING_X;
    /** 玩家背包高度：三行背包 + 4 像素 + 快捷栏。 */
    public static final int PLAYER_INVENTORY_HEIGHT = 4 * SLOT_SIZE + SECTION_GAP;

    /**
     * 机器主页主体区的标准尺寸：宽 {@link #CONTENT_WIDTH}（与玩家背包对齐），高 6 格。
     * 多方块的状态显示窗、机器的三视图设置页都用这个尺寸，切换这两页时窗口不变大小。
     */
    public static final int MACHINE_PAGE_HEIGHT = 6 * SLOT_SIZE;
    public static final int RECIPE_MACHINE_PAGE_BLANK = 14;
    /**
     * 单方块配方机器主页主体区的最小高度：常见的一两格输入输出机器都在这个高度内，窗口一样大；
     * 组装机这类槽多的机器按内容撑高。
     */
    public static final int RECIPE_MACHINE_PAGE_HEIGHT = 2 * SLOT_SIZE + 2 * RECIPE_MACHINE_PAGE_BLANK - SECTION_GAP;
    /** 悬浮栏（dock）离所在区域底边的距离。 */
    public static final int DOCK_MARGIN = 4;
    /** 悬浮栏里的图标按钮边长：按钮面（去掉底部台阶）里正好原尺寸放下一个 16×16 图标。 */
    public static final int DOCK_BUTTON_SIZE = 22;

    // ==================== 画布 ====================
    /** 画布网格最细一级的世界间距、屏幕上允许的最小线距。 */
    public static final float CANVAS_GRID_SIZE = 16;
    public static final float CANVAS_GRID_MIN_PIXELS = 12;
    /** 画布视口的最小边长（拖拽缩放的下限）。 */
    public static final int CANVAS_MIN_SIZE = 3 * SLOT_SIZE;
    /** 画布缩略图：最大占视口的 1/3，并夹在这组宽高之间；离视口右上角的距离。 */
    public static final int CANVAS_MINIMAP_MIN_WIDTH = 2 * SLOT_SIZE;
    public static final int CANVAS_MINIMAP_MAX_WIDTH = 5 * SLOT_SIZE;
    public static final int CANVAS_MINIMAP_MIN_HEIGHT = SLOT_SIZE + GAP * 3;
    public static final int CANVAS_MINIMAP_MAX_HEIGHT = 4 * SLOT_SIZE;
    public static final int CANVAS_MINIMAP_MARGIN = 3;
    public static final int FLOW_PADDING = 4;
    public static final int FLOW_COLUMN_GAP = 8;
    public static final int FLOW_CHANNEL_WIDTH = CONTROL_HEIGHT;
    public static final int FLOW_WIRE_WIDTH = 2;
    public static final int FLOW_ARROW_SIZE = 3;
    public static final int FLOW_JUNCTION_SIZE = 4;
    public static final int FLOW_GUTTER_WIDTH = 8;
    public static final int FLOW_DETOUR_INSET = 12;
    public static final int FLOW_NODE_PADDING = 3;
    public static final int FLOW_NODE_PADDING_TOP = 6;
    /** 窗口左侧配置按钮（GTM 配置面板）的方块边长。 */
    public static final int SIDE_TAB_WIDTH = 24;
    /** 窗口顶部页面标签：宽、高（未选中时），选中时再向上高出 {@link #PAGE_TAB_RAISE}、向下伸进窗口顶边 {@link #PAGE_TAB_OVERLAP}。 */
    public static final int PAGE_TAB_WIDTH = 24;
    public static final int PAGE_TAB_HEIGHT = 22;
    public static final int PAGE_TAB_RAISE = 2;
    public static final int PAGE_TAB_OVERLAP = 3;
    /** 标签太多放不下时，标签宽度最窄压到这里。 */
    public static final int PAGE_TAB_MIN_WIDTH = 20;

    // ==================== 弹出面板 ====================
    /** 弹出面板与主窗口的水平间隔。 */
    public static final int POPUP_GAP = 4;
    /** 弹出面板内边距（Ore 面板标准）：四周 5，底部 7。 */
    public static final int POPUP_PADDING = 5;
    public static final int POPUP_PADDING_BOTTOM = 7;
    public static final int SCREEN_MARGIN = 6;
    /** 弹出面板内容宽度：一个装满 9 槽的区块（162 + 区块左右内边距）。 */
    public static final int POPUP_CONTENT_WIDTH = SLOT_ROW_WIDTH + 2 * PANEL_PADDING;
}
