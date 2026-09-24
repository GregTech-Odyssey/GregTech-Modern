package com.gregtechceu.gtceu.uipro.styletemplate;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.function.BooleanSupplier;

/**
 * 元素的统一外观：形状采用 LDLib2 的 Ore UI（{@link OreSprites}），颜色向原版容器看齐。
 * <ul>
 * <li>窗口、弹出面板：{@link OreSprites#BORDER_7_BRIGHT}（Ore 的圆角厚边，底色换成原版容器的 #C6C6C6）。</li>
 * <li>区块面板：{@link OreSprites#GROOVE}（1 像素灰边、略深的底，像浅浅下凹的区域）。</li>
 * <li>物品槽、滚动条轨道：{@link OreSprites#SLOT_BRIGHT}（原版物品槽配色）；流体槽 {@link OreSprites#FLUID_SLOT} 底色更深，与物品槽区分。</li>
 * <li>按钮：Ore 按钮形状，乘色压暗到约 #9C9C9C（比底色深，和原版一致），悬停/按下再深一档；
 * 绿色为确认、红色为危险操作；深色字无阴影。</li>
 * <li>输入框、数值框：深色框 {@link #INSET}（{@link OreSprites#RECT}），获得焦点时叠白框，白字。</li>
 * <li>文字 #202020：比原版的 #404040 深一些，抗锯齿字体下不发虚。</li>
 * </ul>
 * 配色原则：与原版和 GTM 界面并排也舒服——大面积底色 #C6C6C6、槽 #8B8B8B；
 * 不要出现比底色更亮的大块（Ore 原配色的按钮面、双层边框的高光线都会显得发白），也不要比底色暗得多的大块底色。
 * 所有颜色、贴图都从这里取，元素内部不要写死颜色。
 */
public final class UITheme {

    private UITheme() {}

    /** 正文、标题。 */
    public static final int TEXT = 0xFF202020;
    /** 次要说明文字。 */
    public static final int TEXT_SECONDARY = 0xFF555555;
    /** 区块面板（{@link #PANEL}）里的文字。 */
    public static final int PANEL_TEXT = 0xFF202020;
    /** 深色框里的文字。 */
    public static final int FIELD_TEXT = OreSprites.TEXT_LIGHT;
    /** 深色框里的占位提示文字。 */
    public static final int PLACEHOLDER_TEXT = 0xFF8A8A8A;

    /** 物品槽底图。 */
    public static final IGuiTexture ITEM_SLOT = OreSprites.SLOT_BRIGHT;
    /** 流体槽底图。 */
    public static final IGuiTexture FLUID_SLOT = OreSprites.FLUID_SLOT;
    /** 区块面板底图。 */
    public static final IGuiTexture PANEL = OreSprites.GROOVE;
    /**
     * 状态显示面板（{@code StatusPanel}）的"显示窗"：下凹斜面（左上暗、右下白），底色比区块面板略深。
     * 区块面板是平的细边框，二者并排时一眼能分出"状态"和"设置"。
     */
    public static final IGuiTexture STATUS_PANEL = new OreSprites.Bevel(0xFF7A7A7A, 0xFFFFFFFF, 0xFFB5B5B5);
    /** 输入框、数值框的深色框。 */
    public static final IGuiTexture INSET = OreSprites.RECT;
    /** 滚动条轨道、滑块。 */
    public static final IGuiTexture SCROLL_TRACK = OreSprites.SLOT_BRIGHT;
    public static final IGuiTexture SCROLL_THUMB = OreSprites.BTN_DEFAULT.tinted(OreSprites.BUTTON_TINT);
    /** 机器窗口、弹出面板的外框（里面的字用 {@link #TEXT}）。 */
    public static final IGuiTexture WINDOW = OreSprites.BORDER_7_BRIGHT;
    /**
     * 信息图标图集（{@code info_icons.png}，4 个 18×18 横排）：说明（蓝 i，与 GTM 标题栏机器说明图标逐像素相同）、
     * 警告（黄 !）、错误（红 ×）、成功（绿 ✓）；后三个沿用同一圆形的描边、高光与颗粒，只换颜色和符号。
     */
    private static final ResourceTexture INFO_ICONS = new ResourceTexture(GTCEu.id("textures/gui/uipro/info_icons.png"));
    private static final int INFO_ICON_COUNT = 4;

    /** 第 {@code index} 个信息图标（顺序见 {@link #INFO_ICONS}）。 */
    public static IGuiTexture infoIcon(int index) {
        return INFO_ICONS.getSubTexture((double) index / INFO_ICON_COUNT, 0, 1.0 / INFO_ICON_COUNT, 1);
    }

    /** 步进器、翻页的左右箭头（放在 {@code Button.icon} 里），与按钮文字同色。 */
    public static final IGuiTexture ARROW_LEFT = new OreSprites.Arrow(true, OreSprites.TEXT_DARK);
    public static final IGuiTexture ARROW_RIGHT = new OreSprites.Arrow(false, OreSprites.TEXT_DARK);
    public static final IGuiTexture ARROW_UP = new OreSprites.VerticalArrow(true, OreSprites.TEXT_DARK);
    public static final IGuiTexture ARROW_DOWN = new OreSprites.VerticalArrow(false, OreSprites.TEXT_DARK);
    /** "页面"图标（3×3 方格）：标题栏的页面切换按钮、页面切换页自己的标题图标，与箭头同色。 */
    public static final IGuiTexture PAGES = new OreSprites.Grid(OreSprites.TEXT_DARK);

    /** 按客户端条件在两张图之间切换的贴图（如图标按钮的开 / 关态：收藏星标）。{@code on} 每次绘制时取值。 */
    public static IGuiTexture switching(BooleanSupplier on, IGuiTexture offTexture, IGuiTexture onTexture) {
        return new IGuiTexture() {

            @Override
            @OnlyIn(Dist.CLIENT)
            public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
                (on.getAsBoolean() ? onTexture : offTexture).draw(graphics, mouseX, mouseY, x, y, width, height);
            }
        };
    }

    /** 窗口底色，与 {@link #WINDOW} 的内部一致（标签页与窗口接缝处涂它）。 */
    public static final int WINDOW_FILL = OreSprites.WINDOW_FILL;
    /**
     * 窗口顶部页面标签：与窗口同一套 Ore 边框。选中的标签用窗口底色、向下伸进窗口顶边与窗口连成一体；
     * 未选中的底色暗一档、坐在窗口顶边上，悬停时介于两者之间。
     */
    public static final IGuiTexture PAGE_TAB_SELECTED = OreSprites.BORDER_7_BRIGHT;
    /// 标签悬停底色（页面标签、配置按钮共用）
    private static final int TAB_HOVER_FILL = 0xFFB9B9B9;
    public static final IGuiTexture PAGE_TAB_HOVER = new OreSprites.Refilled(OreSprites.BORDER_7, TAB_HOVER_FILL, 2, 2, 2, 4);
    public static final IGuiTexture PAGE_TAB = new OreSprites.Refilled(OreSprites.BORDER_7, 0xFFA8A8A8, 2, 2, 2, 4);
    /** 窗口左侧配置按钮（GTM 配置面板）的底图，下移 1 像素，让居中摆放的图标落在面板中央。 */
    public static final IGuiTexture CONFIGURATOR_TAB = new OreSprites.Shifted(OreSprites.BORDER_7_BRIGHT, 0, 1);
    /// 浮层（弹出面板、展开的配置项、滚动区缩放角）的绘制高度：盖过物品模型（约 150）和数量文字
    public static final int OVERLAY_Z = 200;
    /// 页内浮层（如 AE 配置格的数量面板）的绘制高度：盖过格子里的物品（约 150）与数量文字（200）
    public static final int PAGE_OVERLAY_Z = 250;
    /// 窗口外框（{@link #WINDOW}，即 BORDER_7）最外圈的描边色
    public static final int WINDOW_OUTLINE = 0xFF181A1B;
    /// 弹出面板指向所属对象的小尖角高度
    public static final int POPUP_NOTCH = 4;
    /// 分段选择（{@code ButtonGroup.compact}）里未选中项悬停时叠的暗色
    public static final int SEGMENT_HOVER = 0x20000000;

    /**
     * 弹出面板顶边上指向所属对象（如被点的格子）的小尖角：尖端 2 像素宽、高 {@link #POPUP_NOTCH}，逐行加宽；
     * 描边、底色与 {@link #WINDOW} 一致，面板顶边在尖角处打通，看起来是面板自己伸出的一角。
     * {@code centerX} 是尖角中线（左右两像素之间），{@code panelTop} 是面板顶边。
     */
    @OnlyIn(Dist.CLIENT)
    public static void drawPopupNotch(GuiGraphics graphics, int centerX, int panelTop) {
        for (int k = 0; k < POPUP_NOTCH; k++) {
            int y = panelTop - POPUP_NOTCH + k, half = k + 1;
            graphics.fill(centerX - half, y, centerX + half, y + 1, WINDOW_OUTLINE);
            if (k > 0) graphics.fill(centerX - half + 1, y, centerX + half - 1, y + 1, WINDOW_FILL);
        }
        graphics.fill(centerX - POPUP_NOTCH + 1, panelTop, centerX + POPUP_NOTCH - 1, panelTop + 2, WINDOW_FILL);
    }

    /** 配置按钮悬停：底色暗一档（不比窗口底色亮）。 */
    public static final IGuiTexture CONFIGURATOR_TAB_HOVER = new OreSprites.Shifted(new OreSprites.Refilled(OreSprites.BORDER_7, TAB_HOVER_FILL, 2, 2, 2, 4), 0, 1);
    /** 配置按钮按下：顶边下移 {@link #CONFIGURATOR_TAB_PRESS_DEPTH} 像素（整块变矮、底边不动）、再暗一档，图标跟着下移同样距离。 */
    public static final int CONFIGURATOR_TAB_PRESS_DEPTH = 2;
    public static final IGuiTexture CONFIGURATOR_TAB_PRESSED = new OreSprites.Shifted(
            new OreSprites.Sunk(new OreSprites.Refilled(OreSprites.BORDER_7, 0xFFADADAD, 2, 2, 2, 4), CONFIGURATOR_TAB_PRESS_DEPTH), 0, 1);
    /** 区块内边距：让开 1 像素细边后留 2 像素空白。 */
    public static final int PANEL_PADDING = 3;
    public static final int PANEL_PADDING_BOTTOM = 3;

    /** 按钮的底部台阶高度：文字/图标要画在台阶以上的区域。 */
    public static final int BUTTON_LIP = 2;

    /** 状态指示色（指示灯等）：在线、正常。 */
    public static final int STATUS_ONLINE = 0xFF55DD55;
    /** 状态指示色：离线、失败。 */
    public static final int STATUS_OFFLINE = 0xFFDD4444;
    /** 状态指示色：需要注意（如权限不足、配置有误）。 */
    public static final int STATUS_WARNING = 0xFFE8B830;
    /** 状态行里浅底上的文字色：正常、注意、错误（比指示灯色深，保证在 #B5B5B5 显示窗上看得清）。 */
    public static final int STATUS_TEXT_GOOD = 0xFF2E7D1E;
    public static final int STATUS_TEXT_WARNING = 0xFF8C5A00;
    public static final int STATUS_TEXT_ERROR = 0xFFA81C1C;
    /** 状态行小灯的描边。 */
    public static final int STATUS_LAMP_OUTLINE = 0xFF373737;

    /** 选中框（{@link #drawSelection}）：金色亮边、深色描边、内部呼吸光的透明度范围与周期。 */
    public static final int SELECTION_COLOR = 0xFFFFC83D;
    /** 描边与原版物品槽的深色边同色：网格里它与邻格的深色线一致，任何一边看起来都一样。 */
    public static final int SELECTION_OUTLINE = 0xFF373737;
    private static final int SELECTION_FILL_ALPHA_MIN = 0x08;
    private static final int SELECTION_FILL_ALPHA_MAX = 0x28;
    private static final long SELECTION_PULSE_MS = 1600;
    /**
     * 三视图（方向配置页）里的面描边：选中的面用 {@link #SELECTION_COLOR}（与槽位选中框同一金色，作用于该面的悬浮栏也用它描边），
     * 鼠标悬停、尚未选中的面用白色。
     */
    public static final int SCENE_HOVER_FACE = 0xFFFFFFFF;

    /** 按钮配色。 */
    public enum ButtonVariant {

        /** 普通操作。 */
        DEFAULT(OreSprites.BTN_DEFAULT.tinted(OreSprites.BUTTON_TINT), OreSprites.BTN_PRESSED.tinted(OreSprites.BUTTON_TINT), OreSprites.TEXT_DARK),
        /** 确认、开启类操作。 */
        CONFIRM(OreSprites.BTN_DEFAULT_GREEN, OreSprites.BTN_PRESSED_GREEN, OreSprites.TEXT_LIGHT),
        /** 清除、删除等危险操作。 */
        DANGER(OreSprites.BTN_DEFAULT_RED, OreSprites.BTN_PRESSED_RED, OreSprites.TEXT_LIGHT);

        private final OreSprites.Sprite base;
        private final OreSprites.Sprite hover;
        private final int textColor;

        ButtonVariant(OreSprites.Sprite base, OreSprites.Sprite hover, int textColor) {
            this.base = base;
            this.hover = hover;
            this.textColor = textColor;
        }

        public int textColor(boolean enabled) {
            return enabled ? textColor : OreSprites.TEXT_DISABLED;
        }
    }

    /** 按钮底图：悬停换深一档，按下再叠一层灰，禁用统一灰色。 */
    @OnlyIn(Dist.CLIENT)
    public static void drawButton(GuiGraphics graphics, int x, int y, int width, int height, ButtonVariant variant,
                                  boolean hovered, boolean pressed, boolean enabled) {
        OreSprites.Sprite sprite;
        if (!enabled) sprite = OreSprites.BTN_DISABLED;
        else if (pressed) sprite = variant.hover.tinted(multiply(variant.hover.tint(), OreSprites.PRESSED_TINT));
        else sprite = hovered ? variant.hover : variant.base;
        sprite.draw(graphics, x, y, width, height);
    }

    /** 选项标记（单选圆点 / 多选勾选框）的边长。 */
    public static final int OPTION_MARK_SIZE = 7;
    private static final int OPTION_MARK_OFF = 0xFFA0A0A0;
    private static final int OPTION_MARK_ON = 0xFFFFFFFF;

    /**
     * 按钮组选项左侧的标记，{@link #OPTION_MARK_SIZE} 见方、深色描边（四角缺一像素，看起来是圆的）：
     * 单选是"灯"——没选中暗灰、选中点亮成白色；多选是勾选框——白底，选中时画深绿色对勾。
     */
    @OnlyIn(Dist.CLIENT)
    public static void drawOptionMark(GuiGraphics graphics, int x, int y, boolean multiple, boolean on) {
        int s = OPTION_MARK_SIZE, r = x + s, b = y + s;
        int outline = STATUS_LAMP_OUTLINE;
        graphics.fill(x + 1, y, r - 1, y + 1, outline);
        graphics.fill(x + 1, b - 1, r - 1, b, outline);
        graphics.fill(x, y + 1, x + 1, b - 1, outline);
        graphics.fill(r - 1, y + 1, r, b - 1, outline);
        if (!multiple) {
            graphics.fill(x + 1, y + 1, r - 1, b - 1, on ? OPTION_MARK_ON : OPTION_MARK_OFF);
            return;
        }
        graphics.fill(x + 1, y + 1, r - 1, b - 1, OPTION_MARK_ON);
        if (!on) return;
        // 5×5 内格里的对勾：左短臂向下，右长臂向上
        int ix = x + 1, iy = y + 1, c = STATUS_TEXT_GOOD;
        int[][] pixels = { { 0, 2 }, { 1, 3 }, { 2, 4 }, { 3, 3 }, { 3, 2 }, { 4, 1 }, { 4, 0 } };
        for (var p : pixels) graphics.fill(ix + p[0], iy + p[1], ix + p[0] + 1, iy + p[1] + 1, c);
    }

    /** 深色框；{@code focused} 时叠白框。 */
    @OnlyIn(Dist.CLIENT)
    public static void drawInset(GuiGraphics graphics, int x, int y, int width, int height, boolean focused) {
        OreSprites.RECT.draw(graphics, x, y, width, height);
        if (focused) OreSprites.WHITE_BORDER.draw(graphics, x, y, width, height);
    }

    /**
     * 统一的选中框，画在物品之上、悬浮提示之下（z = 300）：深色圆角描边 + 金色亮边，内部一层缓慢呼吸的淡金色。
     * 在前景层调用（见 {@code UIElement#drawInForeground}），否则之后才绘制的相邻元素会盖住外扩的描边。
     * <p>
     * 框比元素向外扩 1 像素：原版斜面槽排成网格时，格与格之间是"白色高光 + 深色阴影"两条线，
     * 贴着槽边画框会让上、左两边多出邻格的白线而显得更粗；外扩 1 像素后四边正好各盖住这两条线，上下左右对称。
     */
    @OnlyIn(Dist.CLIENT)
    public static void drawSelection(GuiGraphics graphics, int x, int y, int width, int height) {
        int l = x - 1, t = y - 1, r = x + width + 1, b = y + height + 1;
        double phase = (System.currentTimeMillis() % SELECTION_PULSE_MS) / (double) SELECTION_PULSE_MS;
        int alpha = (int) (SELECTION_FILL_ALPHA_MIN + (SELECTION_FILL_ALPHA_MAX - SELECTION_FILL_ALPHA_MIN) * (0.5 - 0.5 * Math.cos(phase * 2 * Math.PI)));
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 300);
        graphics.fill(l + 2, t + 2, r - 2, b - 2, alpha << 24 | (SELECTION_COLOR & 0xFFFFFF));
        ring(graphics, l, t, r, b, SELECTION_OUTLINE, true);
        ring(graphics, l + 1, t + 1, r - 1, b - 1, SELECTION_COLOR, false);
        pose.popPose();
    }

    /// 只读斜纹：16×16 平铺块，每 4 像素一组"30% 深灰线（原版槽阴影色）+ 紧挨的 22% 白色高光线"，像刻出的凹槽；
    /// 双色是为了浅底（槽、按钮）和深底（输入框）上都看得清
    private static final ResourceLocation DISABLED_HATCH = GTCEu.id("textures/gui/uipro/slot_readonly.png");
    private static final int DISABLED_HATCH_SIZE = 16;
    /// 斜纹的高度层：盖在物品模型（约 150）之上、数量文字（200）之下，有物品时也看得出，数量仍然清楚
    private static final int DISABLED_HATCH_Z = 170;

    /**
     * "只读"标识：在槽的内部（让开 1 像素斜面边）平铺淡斜纹，盖在内容之上、数量文字之下。
     * 用来代替压暗——槽保持明亮的原色，一眼能看出"这格只读、不能点"，里面的物品也看得清。
     * 可与选中框（{@link #drawSelection}）同时出现。
     */
    @OnlyIn(Dist.CLIENT)
    public static void drawDisabled(GuiGraphics graphics, int x, int y, int width, int height) {
        int left = x + 1, top = y + 1, right = x + width - 1, bottom = y + height - 1;
        if (right <= left || bottom <= top) return;
        RenderSystem.enableBlend();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, DISABLED_HATCH_Z);
        for (int ty = top; ty < bottom; ty += DISABLED_HATCH_SIZE) {
            int h = Math.min(DISABLED_HATCH_SIZE, bottom - ty);
            for (int tx = left; tx < right; tx += DISABLED_HATCH_SIZE) {
                int w = Math.min(DISABLED_HATCH_SIZE, right - tx);
                graphics.blit(DISABLED_HATCH, tx, ty, 0, 0, w, h, DISABLED_HATCH_SIZE, DISABLED_HATCH_SIZE);
            }
        }
        pose.popPose();
    }

    /**
     * "可从 EMI 拖入"标记（LDLib2 {@code xeiPhantom}）：槽底部的下箭头（与 GTM 配置槽同一图标），画在槽底图之上、内容之下。
     * {@code darkSlot} 为深色槽（流体槽）时用浅色箭头，否则用深色箭头。
     */
    @OnlyIn(Dist.CLIENT)
    public static void drawXeiPhantom(GuiGraphics graphics, int x, int y, int width, int height, boolean darkSlot) {
        (darkSlot ? GuiTextures.CONFIG_ARROW : GuiTextures.CONFIG_ARROW_DARK).draw(graphics, 0, 0, x, y, width, height);
    }

    /** 拖拽缩放角的边长（滚动区右下角，见 {@code ScrollerView}）。 */
    public static final int RESIZE_GRIP_SIZE = 6;
    /// 拖拽角的点阵：每个点左上一个深色像素、右下一个白色高光像素（与原版槽的明暗方向一致），排成右下角的三角
    private static final int[][] RESIZE_GRIP_DOTS = { { 4, 0 }, { 2, 2 }, { 4, 2 }, { 0, 4 }, { 2, 4 }, { 4, 4 } };

    /**
     * 拖拽缩放角，右下角对齐 ({@code right}, {@code bottom})；悬停或拖拽中时深色部分变成选中金色。
     * 锁定时换成同样大小的直角（沿右、下两边的折角，内侧白色高光），一眼能区分又不抢眼。
     */
    @OnlyIn(Dist.CLIENT)
    public static void drawResizeGrip(GuiGraphics graphics, int right, int bottom, boolean active, boolean locked) {
        int x = right - RESIZE_GRIP_SIZE, y = bottom - RESIZE_GRIP_SIZE;
        int dark = active ? SELECTION_COLOR : SELECTION_OUTLINE;
        if (locked) {
            graphics.fill(right - 2, y, right, bottom, dark);
            graphics.fill(x, bottom - 2, right, bottom, dark);
            graphics.fill(right - 3, y + 1, right - 2, bottom - 2, 0xFFFFFFFF);
            graphics.fill(x + 1, bottom - 3, right - 2, bottom - 2, 0xFFFFFFFF);
            return;
        }
        for (var dot : RESIZE_GRIP_DOTS) {
            graphics.fill(x + dot[0], y + dot[1], x + dot[0] + 1, y + dot[1] + 1, dark);
            graphics.fill(x + dot[0] + 1, y + dot[1] + 1, x + dot[0] + 2, y + dot[1] + 2, 0xFFFFFFFF);
        }
    }

    /** 1 像素矩形边框；{@code roundCorners} 时空出四个角，呈圆角。 */
    @OnlyIn(Dist.CLIENT)
    private static void ring(GuiGraphics graphics, int l, int t, int r, int b, int color, boolean roundCorners) {
        int c = roundCorners ? 1 : 0;
        graphics.fill(l + c, t, r - c, t + 1, color);
        graphics.fill(l + c, b - 1, r - c, b, color);
        graphics.fill(l, t + 1, l + 1, b - 1, color);
        graphics.fill(r - 1, t + 1, r, b - 1, color);
    }

    /// 悬浮栏：底色（与窗口同色）、外框、内侧高光（不画投影）
    public static final int DOCK_PADDING = 3;
    private static final int DOCK_OUTLINE = 0xFF373737;
    private static final int DOCK_HIGHLIGHT = 0xFFFFFFFF;
    /** 悬浮栏里组与组之间的分隔线颜色。 */
    public static final int DOCK_SEPARATOR = 0xFF8A8A8A;

    /**
     * 悬浮栏（dock）底板：与窗口同色的圆角浮起面板——深色圆角外框、内侧一圈白色高光；不画投影。
     */
    @OnlyIn(Dist.CLIENT)
    public static void drawDock(GuiGraphics graphics, int x, int y, int width, int height) {
        drawDock(graphics, x, y, width, height, 0);
    }

    /**
     * 带强调色的悬浮栏底板：{@code accent} 非 0 时外框换成 1 像素宽的强调色圆角边（不画白色高光），
     * 用来把栏和它作用的对象在视觉上连起来（例如三视图里选中的面，见 {@code Dock#setAccentColor}）；为 0 时同
     * {@link #drawDock(GuiGraphics, int, int, int, int)}。
     */
    @OnlyIn(Dist.CLIENT)
    public static void drawDock(GuiGraphics graphics, int x, int y, int width, int height, int accent) {
        int r = x + width, b = y + height;
        graphics.fill(x + 1, y + 1, r - 1, b - 1, WINDOW_FILL);
        if (accent != 0) {
            // 1 像素强调色圆角边，不画白色高光（用户要求 1px，2px 显得粗）
            ring(graphics, x, y, r, b, accent, true);
            return;
        }
        ring(graphics, x, y, r, b, DOCK_OUTLINE, true);
        graphics.fill(x + 1, y + 1, r - 1, y + 2, DOCK_HIGHLIGHT);
        graphics.fill(x + 1, y + 2, x + 2, b - 1, DOCK_HIGHLIGHT);
    }

    /** 居中绘制单行文字，超出 {@code maxWidth} 时截断并加省略号。 */
    @OnlyIn(Dist.CLIENT)
    public static void drawCenteredText(GuiGraphics graphics, String text, int centerX, int y, int maxWidth, int color, boolean shadow) {
        var font = Minecraft.getInstance().font;
        var clipped = clip(font, text, maxWidth);
        graphics.drawString(font, clipped, centerX - font.width(clipped) / 2, y, color, shadow);
    }

    @OnlyIn(Dist.CLIENT)
    public static String clip(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    /**
     * 为暗底写的文字颜色（原版聊天色、GTM 显示屏里的彩色文字）换成在亮底（窗口、状态显示窗）上看得清的颜色，RGB，不含透明度。
     * 原版 16 色逐个对照成同色相的深色版；其他颜色太亮时按亮度压暗、保留色相；本来就够深的颜色不变。
     */
    public static int lightBackgroundColor(int rgb) {
        rgb &= 0xFFFFFF;
        switch (rgb) {
            case 0xFFFFFF: // WHITE
                return TEXT & 0xFFFFFF;
            case 0xAAAAAA: // GRAY
            case 0x555555: // DARK_GRAY
                return TEXT_SECONDARY & 0xFFFFFF;
            case 0xFFFF55: // YELLOW
                return 0x8C6A00;
            case 0xFFAA00: // GOLD
                return 0xA0560A;
            case 0x55FFFF: // AQUA
                return 0x00747A;
            case 0x00AAAA: // DARK_AQUA
                return 0x00666B;
            case 0x55FF55: // GREEN
                return STATUS_TEXT_GOOD & 0xFFFFFF;
            case 0x00AA00: // DARK_GREEN
                return 0x1E6A12;
            case 0xFF5555: // RED
                return STATUS_TEXT_ERROR & 0xFFFFFF;
            case 0xFF55FF: // LIGHT_PURPLE
                return 0x8E2A8E;
            case 0x5555FF: // BLUE
                return 0x2A3FB0;
            default:
                break;
        }
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        // 感知亮度（0~255）；亮于阈值的压暗到目标亮度
        float luminance = 0.299f * r + 0.587f * g + 0.114f * b;
        final float limit = 110;
        if (luminance <= limit) return rgb;
        float k = limit / luminance;
        return Math.round(r * k) << 16 | Math.round(g * k) << 8 | Math.round(b * k);
    }

    /** 两个 ARGB 颜色相乘（-1 视为白色，即不乘色）。 */
    private static int multiply(int a, int b) {
        if (a == -1) return b;
        if (b == -1) return a;
        int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
        int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}
