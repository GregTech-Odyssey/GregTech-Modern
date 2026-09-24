package com.gregtechceu.gtceu.uipro.styletemplate;

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
    public static final int SLOT = 18;
    /** 一行槽位数。 */
    public static final int SLOTS_PER_ROW = 9;
    /** 一整行槽位的宽度（162）。 */
    public static final int SLOT_ROW_WIDTH = SLOT * SLOTS_PER_ROW;

    // ==================== 控件高度 ====================
    /** 标准控件行高：按钮、输入框、步进器、开关。 */
    public static final int CONTROL_HEIGHT = 14;
    /** 单行文字高度（字体行高 9）。 */
    public static final int TEXT_HEIGHT = 9;
    /** 窗口（整个界面）最多占屏幕（GUI 缩放后）的比例：列表自动加高、拖拽缩放都以此为上限。 */
    public static final float MAX_WINDOW_SCREEN_RATIO = 2f / 3f;
    /** 机器窗口底边到屏幕底边至少留出屏幕高度的这个比例，不够时窗口（连同标签栏）整体上移。 */
    public static final float WINDOW_BOTTOM_SCREEN_MARGIN = 0.1f;
    /** 小字的缩放比例（列表里的次要信息，如成员的机器名、坐标）。 */
    public static final float SMALL_TEXT_SCALE = 0.75f;
    /** 一行小字的高度（字体行高 9 × 0.75，取整）。 */
    public static final int SMALL_TEXT_HEIGHT = 7;
    /** 状态行高度：一行文字上下各留 1 像素（与单行 Label 同高），状态面板里逐行紧排。 */
    public static final int STATUS_LINE_HEIGHT = TEXT_HEIGHT + 2;

    // ==================== 控件宽度 ====================
    /** 方形图标按钮 / 步进器箭头的宽度，与控件行高相同。 */
    public static final int ICON_BUTTON = CONTROL_HEIGHT;
    /**
     * 常规文字按钮宽度：四个汉字（36）加两侧留白。按内容宽 162 凑整——
     * 一行放下"步进器（VALUE_WIDTH 时 60）+ 两个按钮"：60 + 2 × 48 + 3 × 2 = 162。
     */
    public static final int BUTTON_WIDTH = 48;
    /** 开关宽度（Ore 开关贴图原宽）。 */
    public static final int SWITCH_WIDTH = 24;
    /** 数字显示框的最小宽度（步进器中间那格，放得下 "10/10"）。 */
    public static final int VALUE_WIDTH = 28;

    // ==================== 间距 ====================
    /** 同一组内元素之间。 */
    public static final int GAP = 2;
    /** 区块与区块之间。 */
    public static final int SECTION_GAP = 4;
    /** 按钮文字两侧留白。 */
    public static final int TEXT_PADDING = 4;

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
    public static final int PLAYER_INVENTORY_HEIGHT = 4 * SLOT + SECTION_GAP;

    /**
     * 机器主页主体区的标准尺寸：宽 {@link #CONTENT_WIDTH}（与玩家背包对齐），高 6 格。
     * 多方块的状态显示窗、机器的三视图设置页都用这个尺寸，切换这两页时窗口不变大小。
     */
    public static final int MACHINE_PAGE_HEIGHT = 6 * SLOT;
    /**
     * 单方块配方机器主页主体区的最小高度（4 格）：常见的一两格输入输出机器都在这个高度内，窗口一样大；
     * 组装机这类槽多的机器按内容撑高。
     */
    public static final int RECIPE_MACHINE_PAGE_HEIGHT = 4 * SLOT;
    /** 悬浮栏（dock）离所在区域底边的距离。 */
    public static final int DOCK_MARGIN = 4;
    /** 窗口左侧配置按钮（GTM 配置面板）的方块边长。 */
    public static final int SIDE_TAB = 24;
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
    /** 弹出面板与屏幕边缘保持的最小距离；面板高度上限 = 屏幕高度 - 2 × 该值，与主窗口高度无关。 */
    public static final int POPUP_SCREEN_MARGIN = 4;
    /** 弹出面板内容宽度：一个装满 9 槽的区块（162 + 区块左右内边距）。 */
    public static final int POPUP_CONTENT_WIDTH = SLOT_ROW_WIDTH + 2 * UITheme.PANEL_PADDING;
}
