package com.gregtechceu.gtceu.uipro.styletemplate;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.canvas.WireStyle;
import com.gregtechceu.gtceu.uipro.render.StateTexture;
import com.gregtechceu.gtceu.uipro.render.UIStates;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

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
    public static int TEXT = 0xFF202020;
    /** 次要说明文字。 */
    public static int TEXT_SECONDARY = 0xFF555555;
    /** 区块面板（{@link #PANEL}）里的文字。 */
    public static int PANEL_TEXT = 0xFF202020;
    public static int LINK_TEXT = 0xFF1B5E9E;
    /** 深色框里的文字。 */
    public static int FIELD_TEXT = OreSprites.TEXT_LIGHT;
    /** 深色框里的占位提示文字。 */
    public static int PLACEHOLDER_TEXT = 0xFF8A8A8A;
    public static int BUTTON_TEXT = OreSprites.TEXT_DARK;
    public static int BUTTON_TEXT_DISABLED = OreSprites.TEXT_DISABLED;
    private static boolean darkSurfaces;

    public static int text() {
        return TEXT;
    }

    public static int textSecondary() {
        return TEXT_SECONDARY;
    }

    public static int panelText() {
        return PANEL_TEXT;
    }

    public static int barProgress() {
        return FLOW_GREEN_LIGHT;
    }

    public static int barSteam() {
        return FLOW_CYAN_LIGHT;
    }

    public static int barEnergy() {
        return FLOW_AMBER_LIGHT;
    }

    public static int barHeat() {
        return BAR_HEAT;
    }

    /** 物品槽底图。 */
    private static IGuiTexture itemSlot = OreSprites.SLOT_BRIGHT;
    public static final IGuiTexture ITEM_SLOT = dynamic(() -> itemSlot);
    /** 流体槽底图。 */
    private static IGuiTexture fluidSlot = OreSprites.FLUID_SLOT;
    public static final IGuiTexture FLUID_SLOT = dynamic(() -> fluidSlot);
    /** 区块面板底图。 */
    private static IGuiTexture panel = OreSprites.GROOVE;
    public static final IGuiTexture PANEL = dynamic(() -> panel);
    /**
     * 状态显示面板（{@code StatusPanel}）的"显示窗"：下凹斜面（左上暗、右下白），底色比区块面板略深。
     * 区块面板是平的细边框，二者并排时一眼能分出"状态"和"设置"。
     */
    private static IGuiTexture statusPanel = new OreSprites.Bevel(0xFF7A7A7A, 0xFFFFFFFF, 0xFFB5B5B5);
    private static IGuiTexture deck = new OreSprites.Plate(0xFF373737, 0xFFFFFFFF, 0xFF7A7A7A, 0xFFBDBDBD);
    private static IGuiTexture etch = new OreSprites.Etch(0xFF7A7A7A, 0xFFFFFFFF);
    public static final IGuiTexture DECK = dynamic(() -> deck);
    public static final IGuiTexture ETCH = dynamic(() -> etch);
    public static int SLOT_GHOST_VEIL = 0x808B8B8B;
    public static final IGuiTexture STATUS_PANEL = dynamic(() -> statusPanel);
    /** 进度条（{@code ProgressBar}）的轨道：与状态显示窗同样的下凹斜面，底色再深一档，填充色压在上面看得清。 */
    private static IGuiTexture progressTrack = new OreSprites.Bevel(0xFF7A7A7A, 0xFFFFFFFF, 0xFFA4A4A4);
    public static final IGuiTexture PROGRESS_TRACK = dynamic(() -> progressTrack);
    private static IGuiTexture displayScreen = new OreSprites.Bevel(0xFF373737, 0xFFFFFFFF, 0xFF21262A);
    public static final IGuiTexture DISPLAY_SCREEN = dynamic(() -> displayScreen);
    public static int SCREEN_TEXT = 0xFFE2E6E8;
    public static int SCREEN_LABEL = 0xFF9DAAB0;
    public static int SCREEN_HEADER = 0xFFFFAA00;
    public static int SCREEN_GOOD = 0xFF55FF55;
    public static int SCREEN_WARNING = 0xFFFFFF55;
    public static int SCREEN_ERROR = 0xFFFF5555;
    public static int SCREEN_TRACK = 0xFF0A0D0E;
    public static final int SCREEN_TRACK_EDGE = 0xFF4B565C;
    public static int SCREEN_DIVIDER = 0xFF3A4449;
    public static final IGuiTexture SCREEN_GAUGE_FRAME = new OreSprites.Bevel(0xFF0C0E10, 0xFF3A4046, 0x00000000);
    public static int BAR_HEAT = 0xFFE3A45A;
    /** 输入框、数值框的深色框。 */
    public static final StateTexture INSET = StateTexture.builder()
            .add(UIStates.FOCUSED, StateTexture.layers(OreSprites.RECT, OreSprites.WHITE_BORDER))
            .build(OreSprites.RECT);
    private static final OreSprites.Sprite SWITCH_ON_HOVER = OreSprites.SWITCH_ON.tinted(OreSprites.PRESSED_TINT);
    private static final OreSprites.Sprite SWITCH_OFF_HOVER = OreSprites.SWITCH_OFF.tinted(OreSprites.PRESSED_TINT);
    public static final StateTexture SWITCH = StateTexture.builder()
            .add(UIStates.CHECKED | UIStates.HOVERED, SWITCH_ON_HOVER)
            .add(UIStates.CHECKED, OreSprites.SWITCH_ON)
            .add(UIStates.HOVERED, SWITCH_OFF_HOVER)
            .build(OreSprites.SWITCH_OFF);
    private static final StateTexture SEGMENT_SELECTED_GREEN = StateTexture.builder()
            .add(UIStates.PRESSED, OreSprites.BTN_PRESSED_SMALL_GREEN)
            .add(UIStates.HOVERED, OreSprites.BTN_HOVER_SMALL_GREEN)
            .build(OreSprites.BTN_DEFAULT_SMALL_GREEN);
    private static StateTexture segmentSelected = SEGMENT_SELECTED_GREEN;
    public static final StateTexture SEGMENT_SELECTED = StateTexture.dynamic(() -> segmentSelected);
    /** 滚动条轨道、滑块。 */
    private static IGuiTexture scrollTrack = OreSprites.SLOT_BRIGHT;
    public static final IGuiTexture SCROLL_TRACK = dynamic(() -> scrollTrack);
    private static IGuiTexture scrollThumb = OreSprites.BTN_DEFAULT.tinted(OreSprites.BUTTON_TINT);
    public static final IGuiTexture SCROLL_THUMB = dynamic(() -> scrollThumb);
    /** 机器窗口、弹出面板的外框（里面的字用 {@link #TEXT}）。 */
    private static IGuiTexture window = OreSprites.BORDER_7_BRIGHT;
    public static final IGuiTexture WINDOW = dynamic(() -> window);
    private static ResourceLocation statusIcons = GTCEu.id("textures/gui/uipro/status_icons.png");
    private static ResourceLocation calloutIcons = GTCEu.id("textures/gui/uipro/callout_icons.png");
    private static final WidgetIconAtlas STATUS_ICONS = new WidgetIconAtlas(() -> statusIcons, 1);

    public static IGuiTexture infoIcon(int index) {
        return STATUS_ICONS.pixelIcon(0, index);
    }

    /** 步进器、翻页的左右箭头（放在 {@code Button.icon} 里），与按钮文字同色。 */
    private static IGuiTexture arrowLeft = new OreSprites.Arrow(true, BUTTON_TEXT);
    private static IGuiTexture arrowRight = new OreSprites.Arrow(false, BUTTON_TEXT);
    private static IGuiTexture linkArrow = new OreSprites.Arrow(false, LINK_TEXT);
    private static IGuiTexture arrowUp = new OreSprites.VerticalArrow(true, BUTTON_TEXT);
    private static IGuiTexture arrowDown = new OreSprites.VerticalArrow(false, BUTTON_TEXT);
    public static final IGuiTexture ARROW_LEFT = dynamic(() -> arrowLeft);
    public static final IGuiTexture ARROW_RIGHT = dynamic(() -> arrowRight);
    public static final IGuiTexture LINK_ARROW = dynamic(() -> linkArrow);
    public static final IGuiTexture ARROW_UP = dynamic(() -> arrowUp);
    public static final IGuiTexture ARROW_DOWN = dynamic(() -> arrowDown);
    /** "页面"图标（3×3 方格）：标题栏的页面切换按钮、页面切换页自己的标题图标，与箭头同色。 */
    private static IGuiTexture pages = new OreSprites.Grid(BUTTON_TEXT);
    public static final IGuiTexture PAGES = dynamic(() -> pages);

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

    /**
     * Applies the selected colour scheme and material pack. This is called by {@link UIStyleManager} on the
     * render thread after resources reload; the public textures stay stable delegating handles so existing widgets
     * do not retain stale panel or slot backgrounds.
     */
    static void applyStyle(Map<String, Integer> colors, UIStyleManager.TexturePack textures) {
        TEXT = color(colors, "text");
        TEXT_SECONDARY = color(colors, "text_secondary");
        PANEL_TEXT = color(colors, "panel_text");
        LINK_TEXT = color(colors, "link_text");
        FIELD_TEXT = color(colors, "field_text");
        PLACEHOLDER_TEXT = color(colors, "placeholder_text");
        SCREEN_TEXT = color(colors, "screen_text");
        SCREEN_LABEL = color(colors, "screen_label");
        SCREEN_HEADER = color(colors, "screen_header");
        SCREEN_GOOD = color(colors, "screen_good");
        SCREEN_WARNING = color(colors, "screen_warning");
        SCREEN_ERROR = color(colors, "screen_error");
        SCREEN_TRACK = color(colors, "screen_track");
        SCREEN_DIVIDER = color(colors, "screen_divider");
        BAR_HEAT = color(colors, "bar_heat");
        BUTTON_TEXT = color(colors, "button_text");
        BUTTON_TEXT_DISABLED = color(colors, "button_text_disabled");
        WINDOW_FILL = color(colors, "window_fill");
        darkSurfaces = luminance(WINDOW_FILL & 0xFFFFFF) < 128;
        WINDOW_OUTLINE = color(colors, "window_outline");
        TAB_FILL = color(colors, "tab_fill");
        TAB_HOVER_FILL = color(colors, "tab_hover_fill");
        TAB_PRESSED_FILL = color(colors, "tab_pressed_fill");
        SEGMENT_HOVER = color(colors, "segment_hover");
        int segmentTint = color(colors, "segment_selected_tint");
        segmentSelected = segmentTint == 0 ? SEGMENT_SELECTED_GREEN : StateTexture.builder()
                .add(UIStates.PRESSED, OreSprites.BTN_PRESSED_SMALL.tinted(multiply(segmentTint, OreSprites.PRESSED_TINT)))
                .add(UIStates.HOVERED, OreSprites.BTN_HOVER_SMALL.tinted(segmentTint))
                .build(OreSprites.BTN_DEFAULT_SMALL.tinted(segmentTint));
        STATUS_ONLINE = color(colors, "status_online");
        STATUS_OFFLINE = color(colors, "status_offline");
        STATUS_WARNING = color(colors, "status_warning");
        STATUS_TEXT_GOOD = color(colors, "status_text_good");
        STATUS_TEXT_WARNING = color(colors, "status_text_warning");
        STATUS_TEXT_ERROR = color(colors, "status_text_error");
        STATUS_LAMP_OUTLINE = color(colors, "status_lamp_outline");
        SELECTION_COLOR = color(colors, "selection_color");
        SELECTION_OUTLINE = color(colors, "selection_outline");
        DOCK_OUTLINE = color(colors, "dock_outline");
        DOCK_HIGHLIGHT = color(colors, "dock_highlight");
        DOCK_SEPARATOR = color(colors, "dock_separator");
        CANVAS_FILL = color(colors, "canvas_fill");
        CANVAS_GRID_LINE = color(colors, "canvas_grid_line");
        CANVAS_GRID_ACCENT = color(colors, "canvas_grid_accent");
        CANVAS_MINIMAP_FILL = color(colors, "canvas_minimap_fill");
        CANVAS_MINIMAP_BORDER = color(colors, "canvas_minimap_border");
        CANVAS_MINIMAP_VIEWPORT = color(colors, "canvas_minimap_viewport");
        CANVAS_ITEM_BLOCK = color(colors, "canvas_item_block");
        SLOT_HOVER_OVERLAY = color(colors, "slot_hover_overlay");
        MAP_ORBIT = color(colors, "map_orbit");
        MAP_ORBIT_FAR = color(colors, "map_orbit_far");
        MAP_LABEL = color(colors, "map_label");
        MAP_LABEL_DIM = color(colors, "map_label_dim");
        MAP_CURRENT = color(colors, "map_current");
        MAP_REALM = color(colors, "map_realm");
        MAP_SKY_TOP = color(colors, "map_sky_top");
        MAP_SKY_BOTTOM = color(colors, "map_sky_bottom");
        MAP_SPRITE_OUTLINE = color(colors, "map_sprite_outline");
        MAP_HOVER_FRAME = color(colors, "map_hover_frame");
        MAP_PLATE = color(colors, "map_plate");
        MAP_PLATE_EDGE = color(colors, "map_plate_edge");
        MAP_LINK_IDLE = color(colors, "map_link_idle");
        MAP_LINK_LOW = color(colors, "map_link_low");
        MAP_LINK_MID = color(colors, "map_link_mid");
        MAP_LINK_HIGH = color(colors, "map_link_high");
        MAP_LINK_CORE = color(colors, "map_link_core");
        MAP_LINK_DIM = color(colors, "map_link_dim");
        MAP_SPARK = color(colors, "map_spark");
        MAP_SPARK_TAIL = color(colors, "map_spark_tail");
        MAP_GUIDE = color(colors, "map_guide");
        MAP_GUIDE_HOVER = color(colors, "map_guide_hover");
        MAP_PATH = color(colors, "map_path");
        MAP_SATURATED = color(colors, "map_saturated");
        MAP_NET_UP = color(colors, "map_net_up");
        MAP_NET_DOWN = color(colors, "map_net_down");

        OreSprites.setTexture(textures.atlas());
        statusIcons = textures.statusIcons();
        calloutIcons = textures.calloutIcons();
        viewIcons = textures.viewIcons();
        disabledHatch = textures.disabledHatch();
        arrowLeft = new OreSprites.Arrow(true, BUTTON_TEXT);
        arrowRight = new OreSprites.Arrow(false, BUTTON_TEXT);
        linkArrow = new OreSprites.Arrow(false, LINK_TEXT);
        arrowUp = new OreSprites.VerticalArrow(true, BUTTON_TEXT);
        arrowDown = new OreSprites.VerticalArrow(false, BUTTON_TEXT);
        pages = new OreSprites.Grid(BUTTON_TEXT);

        DIVIDER = color(colors, "divider");
        CARD_FILL = color(colors, "card_fill");
        CARD_OUTLINE = color(colors, "card_outline");
        CARD_HIGHLIGHT = color(colors, "card_highlight");
        CARD_SHADOW = color(colors, "card_shadow");
        FLOW_NODE_FILL = color(colors, "flow_node_fill");
        FLOW_NODE_OUTLINE = color(colors, "flow_node_outline");
        FLOW_NODE_HIGHLIGHT = color(colors, "flow_node_highlight");
        FLOW_NODE_SHADE = color(colors, "flow_node_shade");
        FLOW_WIRE_IDLE = WireStyle.solid(color(colors, "flow_wire_idle"));
        FLOW_WIRE_DISABLED = WireStyle.patterned(color(colors, "flow_wire_disabled"), WireStyle.Pattern.DOTTED);
        FLOW_STRIP_EDGE = color(colors, "flow_strip_edge");
        FLOW_OFF_LIGHT = color(colors, "flow_off_light");
        FLOW_OFF_MID = color(colors, "flow_off_mid");
        FLOW_GREEN_LIGHT = color(colors, "flow_green_light");
        FLOW_GREEN_MID = color(colors, "flow_green_mid");
        FLOW_CYAN_LIGHT = color(colors, "flow_cyan_light");
        FLOW_CYAN_MID = color(colors, "flow_cyan_mid");
        FLOW_CYAN_BRIGHT = color(colors, "flow_cyan_bright");
        FLOW_AMBER_LIGHT = color(colors, "flow_amber_light");
        FLOW_AMBER_MID = color(colors, "flow_amber_mid");
        FLOW_AMBER_DARK = color(colors, "flow_amber_dark");
        FLOW_RED_LIGHT = color(colors, "flow_red_light");
        FLOW_RED_MID = color(colors, "flow_red_mid");
        FLOW_RED_DARK = color(colors, "flow_red_dark");
        FLOW_WIRE_READY = WireStyle.solid(FLOW_GREEN_MID);
        FLOW_WIRE_ACTIVE = WireStyle.flowing(FLOW_CYAN_MID, 0xFFA4BDC1, 0xFF8FC9D2, 0xFFF2FDFF, FLOW_CYAN_BRIGHT);
        FLOW_WIRE_WARNING = WireStyle.solid(FLOW_AMBER_MID);
        FLOW_WIRE_MISSING = WireStyle.patterned(FLOW_RED_MID, WireStyle.Pattern.DASHED);

        int bevelDark = color(colors, "bevel_dark");
        int bevelLight = color(colors, "bevel_light");
        int slotBevelDark = color(colors, "slot_bevel_dark");
        int slotBevelLight = color(colors, "slot_bevel_light");
        itemSlot = new OreSprites.Bevel(slotBevelDark, slotBevelLight, color(colors, "slot_fill"));
        fluidSlot = new OreSprites.Bevel(slotBevelDark, slotBevelLight, color(colors, "fluid_slot_fill"));
        panel = new OreSprites.Bevel(color(colors, "panel_outline"), color(colors, "panel_outline"), color(colors, "panel_fill"));
        statusPanel = new OreSprites.Bevel(bevelDark, bevelLight, color(colors, "status_panel_fill"));
        deck = new OreSprites.Plate(slotBevelDark, bevelLight, bevelDark, color(colors, "panel_fill"));
        etch = new OreSprites.Etch(bevelDark, bevelLight);
        SLOT_GHOST_VEIL = (color(colors, "slot_fill") & 0xFFFFFF) | 0x80000000;
        progressTrack = new OreSprites.Bevel(bevelDark, bevelLight, color(colors, "progress_track_fill"));
        displayScreen = new OreSprites.Bevel(bevelDark, bevelLight, color(colors, "display_screen_fill"));
        scrollTrack = itemSlot;
        updateButtonSprites(color(colors, "button_tint"));
        scrollThumb = buttonDefault;
        window = new OreSprites.Refilled(OreSprites.BORDER_7, WINDOW_FILL, 2, 2, 2, 4);
        pageTabSelected = new OreSprites.Refilled(OreSprites.TAB_SELECTED, WINDOW_FILL, 2, 2, 2, 0);
        pageTab = new OreSprites.Refilled(OreSprites.BORDER_7, TAB_FILL, 2, 2, 2, 4);
        pageTabHover = new OreSprites.Refilled(OreSprites.BORDER_7, TAB_HOVER_FILL, 2, 2, 2, 4);
        configuratorTab = new OreSprites.Shifted(window, 0, 1);
        configuratorTabHover = new OreSprites.Shifted(pageTabHover, 0, 1);
        configuratorTabPressed = new OreSprites.Shifted(
                new OreSprites.Sunk(new OreSprites.Refilled(OreSprites.BORDER_7, TAB_PRESSED_FILL, 2, 2, 2, 4), UISizes.CONFIGURATOR_TAB_PRESS_DEPTH), 0, 1);
        configuratorTabLatched = new OreSprites.Shifted(new OreSprites.Refilled(OreSprites.LATCHED, color(colors, "latched_fill"), 2, 2, 2, 2), 0, 1);
        canvas = new OreSprites.Bevel(bevelDark, bevelLight, CANVAS_FILL);
        pageTabStates = pageTabStates();
        configuratorTabStates = configuratorTabStates();
    }

    private static int color(Map<String, Integer> colors, String key) {
        var value = schemeColor(colors, key);
        return value != null ? value : UIStyleManager.DEFAULT_COLORS.get(key);
    }

    @Nullable
    private static Integer schemeColor(Map<String, Integer> colors, String key) {
        var value = colors.get(key);
        if (value != null) return value;
        var fallback = UIStyleManager.FALLBACK_KEYS.get(key);
        return fallback != null ? schemeColor(colors, fallback) : null;
    }

    private static IGuiTexture dynamic(Supplier<IGuiTexture> texture) {
        return new IGuiTexture() {

            @Override
            @OnlyIn(Dist.CLIENT)
            public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
                texture.get().draw(graphics, mouseX, mouseY, x, y, width, height);
            }
        };
    }

    /** Material-pack callout icon atlas. Kept package-visible for {@code CalloutBubble}. */
    public static ResourceLocation calloutIcons() {
        return calloutIcons;
    }

    /** 窗口底色，与 {@link #WINDOW} 的内部一致（标签页与窗口接缝处涂它）。 */
    public static int WINDOW_FILL = OreSprites.WINDOW_FILL;
    /**
     * 窗口顶部页面标签：与窗口同一套 Ore 边框。选中的标签用窗口底色、向下伸进窗口顶边与窗口连成一体；
     * 未选中的底色暗一档、坐在窗口顶边上，悬停时介于两者之间。
     */
    private static IGuiTexture pageTabSelected = new OreSprites.Refilled(OreSprites.TAB_SELECTED, OreSprites.WINDOW_FILL, 2, 2, 2, 0);
    /// 标签悬停底色（页面标签、配置按钮共用）
    private static int TAB_HOVER_FILL = 0xFFB9B9B9;
    private static int TAB_FILL = 0xFFA8A8A8;
    private static int TAB_PRESSED_FILL = 0xFFADADAD;
    private static IGuiTexture pageTabHover = new OreSprites.Refilled(OreSprites.BORDER_7, TAB_HOVER_FILL, 2, 2, 2, 4);
    private static IGuiTexture pageTab = new OreSprites.Refilled(OreSprites.BORDER_7, TAB_FILL, 2, 2, 2, 4);
    /** 窗口左侧配置按钮（GTM 配置面板）的底图，下移 1 像素，让居中摆放的图标落在面板中央。 */
    private static IGuiTexture configuratorTab = new OreSprites.Shifted(OreSprites.BORDER_7_BRIGHT, 0, 1);
    /// 窗口外框（{@link #WINDOW}，即 BORDER_7）最外圈的描边色
    public static int WINDOW_OUTLINE = 0xFF181A1B;

    /** Pixel bracket across the gap between two independently framed window panels. */
    @OnlyIn(Dist.CLIENT)
    public static void drawWindowConnector(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y - 2, x + width, y + 2, WINDOW_OUTLINE);
        graphics.fill(x, y - 1, x + width, y, CARD_HIGHLIGHT);
        graphics.fill(x, y, x + width, y + 1, CARD_SHADOW);
    }

    /// 分段选择（{@code ButtonGroup.compact}）里未选中项悬停时叠的暗色
    public static int SEGMENT_HOVER = 0x20000000;

    /** 配置按钮悬停：底色暗一档（不比窗口底色亮）。 */
    private static IGuiTexture configuratorTabHover = new OreSprites.Shifted(new OreSprites.Refilled(OreSprites.BORDER_7, TAB_HOVER_FILL, 2, 2, 2, 4), 0, 1);
    /** 配置按钮按下：顶边下移 {@link UISizes#CONFIGURATOR_TAB_PRESS_DEPTH} 像素（整块变矮、底边不动）、再暗一档，图标跟着下移同样距离。 */
    private static IGuiTexture configuratorTabPressed = new OreSprites.Shifted(
            new OreSprites.Sunk(new OreSprites.Refilled(OreSprites.BORDER_7, TAB_PRESSED_FILL, 2, 2, 2, 4), UISizes.CONFIGURATOR_TAB_PRESS_DEPTH), 0, 1);
    private static IGuiTexture configuratorTabLatched = new OreSprites.Shifted(
            new OreSprites.Refilled(OreSprites.LATCHED, 0xFF969696, 2, 2, 2, 2), 0, 1);
    private static StateTexture pageTabStates = pageTabStates();
    public static final StateTexture PAGE_TAB = StateTexture.dynamic(() -> pageTabStates);
    private static StateTexture configuratorTabStates = configuratorTabStates();
    public static final StateTexture CONFIGURATOR_TAB = StateTexture.dynamic(() -> configuratorTabStates);

    private static StateTexture pageTabStates() {
        return StateTexture.builder()
                .add(UIStates.SELECTED, pageTabSelected)
                .add(UIStates.HOVERED, pageTabHover)
                .build(pageTab);
    }

    private static StateTexture configuratorTabStates() {
        return StateTexture.builder()
                .add(UIStates.LATCHED, configuratorTabLatched)
                .add(UIStates.PRESSED, configuratorTabPressed)
                .add(UIStates.HOVERED, configuratorTabHover)
                .build(configuratorTab);
    }

    /** 状态指示色（指示灯等）：在线、正常。 */
    public static int STATUS_ONLINE = 0xFF55DD55;
    /** 状态指示色：离线、失败。 */
    public static int STATUS_OFFLINE = 0xFFDD4444;
    /** 状态指示色：需要注意（如权限不足、配置有误）。 */
    public static int STATUS_WARNING = 0xFFE8B830;
    /** 状态行里浅底上的文字色：正常、注意、错误（比指示灯色深，保证在 #B5B5B5 显示窗上看得清）。 */
    public static int STATUS_TEXT_GOOD = 0xFF2E7D1E;
    public static int STATUS_TEXT_WARNING = 0xFF8C5A00;
    public static int STATUS_TEXT_ERROR = 0xFFA81C1C;
    /** 状态行小灯的描边。 */
    public static int STATUS_LAMP_OUTLINE = 0xFF373737;

    public static int SELECTION_COLOR = 0xFFFFC83D;
    /** 描边与原版物品槽的深色边同色：网格里它与邻格的深色线一致，任何一边看起来都一样。 */
    public static int SELECTION_OUTLINE = 0xFF373737;

    /** 按钮配色。 */
    public enum ButtonVariant {

        /** 普通操作。 */
        DEFAULT,
        /** 确认、开启类操作。 */
        CONFIRM,
        /** 清除、删除等危险操作。 */
        DANGER;

        public static ButtonVariant of(Level level) {
            return switch (level) {
                case GOOD -> CONFIRM;
                case ERROR -> DANGER;
                default -> DEFAULT;
            };
        }

        public int textColor(boolean enabled) {
            if (!enabled) return BUTTON_TEXT_DISABLED;
            return this == DEFAULT ? BUTTON_TEXT : OreSprites.TEXT_LIGHT;
        }

        public StateTexture texture() {
            return switch (this) {
                case DEFAULT -> BUTTON;
                case CONFIRM -> BUTTON_CONFIRM;
                case DANGER -> BUTTON_DANGER;
            };
        }
    }

    private static OreSprites.Sprite buttonDefault = OreSprites.BTN_DEFAULT.tinted(OreSprites.BUTTON_TINT);
    private static OreSprites.Sprite buttonDefaultHover = OreSprites.BTN_PRESSED.tinted(OreSprites.BUTTON_TINT);
    private static OreSprites.Sprite buttonDefaultPressed = buttonDefaultHover.tinted(multiply(buttonDefaultHover.tint(), OreSprites.PRESSED_TINT));
    private static final OreSprites.Sprite BUTTON_CONFIRM_PRESSED = OreSprites.BTN_PRESSED_GREEN.tinted(multiply(OreSprites.BTN_PRESSED_GREEN.tint(), OreSprites.PRESSED_TINT));
    private static final OreSprites.Sprite BUTTON_DANGER_PRESSED = OreSprites.BTN_PRESSED_RED.tinted(multiply(OreSprites.BTN_PRESSED_RED.tint(), OreSprites.PRESSED_TINT));

    private static StateTexture buttonStates = buttonStates(buttonDefault, buttonDefaultHover, buttonDefaultPressed);
    public static final StateTexture BUTTON = StateTexture.dynamic(() -> buttonStates);
    public static final StateTexture BUTTON_CONFIRM = buttonStates(OreSprites.BTN_DEFAULT_GREEN, OreSprites.BTN_PRESSED_GREEN, BUTTON_CONFIRM_PRESSED);
    public static final StateTexture BUTTON_DANGER = buttonStates(OreSprites.BTN_DEFAULT_RED, OreSprites.BTN_PRESSED_RED, BUTTON_DANGER_PRESSED);

    private static StateTexture buttonStates(IGuiTexture base, IGuiTexture hover, IGuiTexture pressed) {
        return StateTexture.builder()
                .add(UIStates.PRESSED, pressed)
                .add(UIStates.HOVERED, hover)
                .build(base);
    }

    private static void updateButtonSprites(int tint) {
        buttonDefault = OreSprites.BTN_DEFAULT.tinted(tint);
        buttonDefaultHover = OreSprites.BTN_PRESSED.tinted(tint);
        buttonDefaultPressed = buttonDefaultHover.tinted(multiply(buttonDefaultHover.tint(), OreSprites.PRESSED_TINT));
        buttonStates = buttonStates(buttonDefault, buttonDefaultHover, buttonDefaultPressed);
    }

    /// 只读斜纹：16×16 平铺块，每 4 像素一组"30% 深灰线（原版槽阴影色）+ 紧挨的 22% 白色高光线"，像刻出的凹槽；
    /// 双色是为了浅底（槽、按钮）和深底（输入框）上都看得清
    private static ResourceLocation disabledHatch = GTCEu.id("textures/gui/uipro/slot_readonly.png");

    public static ResourceLocation disabledHatch() {
        return disabledHatch;
    }

    /// 悬浮栏：底色（与窗口同色）、外框、内侧高光（不画投影）
    public static int DOCK_OUTLINE = 0xFF373737;
    public static int DOCK_HIGHLIGHT = 0xFFFFFFFF;
    /** 悬浮栏里组与组之间的分隔线颜色。 */
    public static int DOCK_SEPARATOR = 0xFF8A8A8A;

    // ==================== 画布（CanvasView） ====================

    /**
     * 画布底色：比窗口底色暗一档的浅灰。画布里的内容（节点、连线）自带颜色，底色要衬得住又不能成为"一大块暗色"。
     */
    public static int CANVAS_FILL = 0xFFB4B4B4;
    /** 画布外框：与状态显示窗同样的下凹斜面（左上暗、右下白），一眼看出是一块"视口"。 */
    private static IGuiTexture canvas = new OreSprites.Bevel(0xFF7A7A7A, 0xFFFFFFFF, CANVAS_FILL);
    public static final IGuiTexture CANVAS = dynamic(() -> canvas);
    /** 画布网格：细线、主线都是半透明深色，叠在底色上只比底色深一点点。 */
    public static int CANVAS_GRID_LINE = 0x14000000;
    public static int CANVAS_GRID_ACCENT = 0x26000000;
    /** 缩略图：窗口底色（略透明）、深色 1 像素边、视口框用选中金色。 */
    public static int CANVAS_MINIMAP_FILL = 0xE6C6C6C6;
    public static int CANVAS_MINIMAP_BORDER = 0xFF373737;
    public static int CANVAS_MINIMAP_VIEWPORT = SELECTION_COLOR;

    /**
     * 视图按钮图标（{@code uipro/view_icons.png}，{@link UISizes#VIEW_ICON_SIZE} 见方横排）：缩小、放大、适应全部、缩略图、定位。
     * 按机器小组件图标的画法（1 像素深色方角描边、细线、降饱和、按外框自动居中），放在画布悬浮栏
     * {@link UISizes#DOCK_BUTTON_SIZE} 见方的按钮里原尺寸显示。
     */
    public static final IGuiTexture SCREEN_LOGO = new DynamicResourceTexture(() -> GTCEu.id("textures/gui/uipro/gto_logo_screen.png"), 0, 0, 1, 1);

    private static ResourceLocation viewIcons = GTCEu.id("textures/gui/uipro/view_icons.png");
    private static final int VIEW_ICON_COUNT = 5;
    public static final IGuiTexture CANVAS_ZOOM_OUT = viewIcon(0);
    public static final IGuiTexture CANVAS_ZOOM_IN = viewIcon(1);
    public static final IGuiTexture CANVAS_FIT = viewIcon(2);
    public static final IGuiTexture CANVAS_MINIMAP = viewIcon(3);
    public static final IGuiTexture CANVAS_LOCATE = viewIcon(4);

    private static IGuiTexture viewIcon(int index) {
        return new DynamicResourceTexture(() -> viewIcons, (double) index / VIEW_ICON_COUNT, 0, 1.0 / VIEW_ICON_COUNT, 1);
    }

    public static int DIVIDER = 0xFF8B8B8B;
    public static int CARD_FILL = 0xFFC6C6C6;
    public static int CARD_OUTLINE = 0xFF000000;
    public static int CARD_HIGHLIGHT = 0xFFFFFFFF;
    public static int CARD_SHADOW = 0xFF555555;

    public static int FLOW_NODE_FILL = WINDOW_FILL;
    public static int FLOW_NODE_OUTLINE = 0xFF373737;
    public static int FLOW_NODE_HIGHLIGHT = 0xFFDCDCDC;
    public static int FLOW_NODE_SHADE = 0xFFA2A2A2;
    public static int FLOW_STRIP_EDGE = 0xFF4C5156;
    public static int FLOW_OFF_LIGHT = 0xFF7C8287, FLOW_OFF_MID = 0xFF61676C;
    public static int FLOW_GREEN_LIGHT = 0xFF93C987, FLOW_GREEN_MID = 0xFF62A15A;
    public static int FLOW_CYAN_LIGHT = 0xFF86CEDA, FLOW_CYAN_MID = 0xFF419CAD, FLOW_CYAN_BRIGHT = 0xFFBDEAF0;
    public static int FLOW_AMBER_LIGHT = 0xFFECC66E, FLOW_AMBER_MID = 0xFFD19B36, FLOW_AMBER_DARK = 0xFFA2711F;
    public static int FLOW_RED_LIGHT = 0xFFE27F6C, FLOW_RED_MID = 0xFFC24D3E, FLOW_RED_DARK = 0xFF8F3328;
    public static int FACE_NET_OFF = 0x38000000;
    public static int SIDE_ITEM_OUTPUT = 0xFFFF7A1A;
    public static int SIDE_FLUID_OUTPUT = 0xFF2F9BFF;
    public static int SIDE_OUTPUT_IDLE_ALPHA = 0x90000000;
    public static int SIDE_HOVER_FRAME = 0xD0FFFFFF;
    public static int SIDE_HOVER_FILL = 0x30FFFFFF;
    public static int LIST_ROW_FILL = 0x14000000;
    public static WireStyle FLOW_WIRE_DISABLED = WireStyle.patterned(0xFF9A9A9A, WireStyle.Pattern.DOTTED);
    public static WireStyle FLOW_WIRE_IDLE = WireStyle.solid(0xFF8A8A8A);
    public static WireStyle FLOW_WIRE_READY = WireStyle.solid(FLOW_GREEN_MID);
    public static WireStyle FLOW_WIRE_ACTIVE = WireStyle.flowing(FLOW_CYAN_MID, 0xFFA4BDC1, 0xFF8FC9D2, 0xFFF2FDFF, FLOW_CYAN_BRIGHT);
    public static WireStyle FLOW_WIRE_WARNING = WireStyle.solid(FLOW_AMBER_MID);
    public static WireStyle FLOW_WIRE_MISSING = WireStyle.patterned(FLOW_RED_MID, WireStyle.Pattern.DASHED);

    public static int TAB_HINT_GLOW = SELECTION_COLOR;
    public static int TAB_HINT_SPARK = 0xFFFFF4CF;
    public static int TAB_HINT_DIM = 0xFF8C6A1E;
    public static final int TAB_HINT_HALO_ALPHA_MIN = 0x18, TAB_HINT_HALO_ALPHA_MAX = 0x50;

    /** 画布项在最小细节层级与缩略图里的默认色（与物品槽同色）。 */
    public static int CANVAS_ITEM_BLOCK = 0xFF8B8B8B;
    /** 槽位类格子的悬停蒙层（与原版物品槽一致，画在物品之上、只写颜色不写透明度）。 */
    public static int SLOT_HOVER_OVERLAY = 0x80FFFFFF;
    public static int MAP_ORBIT = 0xFF2C3354;
    public static int MAP_ORBIT_FAR = 0xFF1E2238;
    public static int MAP_LABEL = 0xFFE0E0E0;
    public static int MAP_LABEL_DIM = 0xFFA0A0A0;
    public static int MAP_CURRENT = 0xFF55FFFF;
    public static int MAP_REALM = 0xFFB48CFF;
    public static int MAP_SKY_TOP = 0xFF080A14;
    public static int MAP_SKY_BOTTOM = 0xFF0B0E1E;
    public static int MAP_SPRITE_OUTLINE = 0xFF05070F;
    public static int MAP_HOVER_FRAME = 0xA0FFFFFF;
    public static int MAP_PLATE = 0xC0050816;
    public static int MAP_PLATE_EDGE = 0xFF2F4378;
    public static int MAP_LINK_IDLE = 0xFF2A4C7A;
    public static int MAP_LINK_LOW = 0xFF3C7FC0;
    public static int MAP_LINK_MID = 0xFF4FA6F0;
    public static int MAP_LINK_HIGH = 0xFF7FD4FF;
    public static int MAP_LINK_CORE = 0xFFD8F4FF;
    public static int MAP_LINK_DIM = 0xFF1E3554;
    public static int MAP_SPARK = 0xFFF2FDFF;
    public static int MAP_SPARK_TAIL = 0xFF7FD4FF;
    public static int MAP_GUIDE = 0x805A6178;
    public static int MAP_GUIDE_HOVER = 0xFFC8D0E0;
    public static int MAP_PATH = 0xFFFFD27A;
    public static int MAP_SATURATED = 0xFFFFC857;
    public static int MAP_NET_UP = 0xFF55FF55;
    public static int MAP_NET_DOWN = 0xFFFF9A55;

    /**
     * 为暗底写的文字颜色（原版聊天色、GTM 显示屏里的彩色文字）换成在亮底（窗口、状态显示窗）上看得清的颜色，RGB，不含透明度。
     * 原版 16 色逐个对照成同色相的深色版；其他颜色太亮时按亮度压暗、保留色相；本来就够深的颜色不变。
     */
    public static int lightBackgroundColor(int rgb) {
        rgb &= 0xFFFFFF;
        if (darkSurfaces) return rgb == 0xFFFFFF ? TEXT & 0xFFFFFF : darkBackgroundColor(rgb);
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

    public static int darkBackgroundColor(int rgb) {
        rgb &= 0xFFFFFF;
        if (rgb == 0xFFFFFF) return SCREEN_TEXT & 0xFFFFFF;
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        float luminance = 0.299f * r + 0.587f * g + 0.114f * b;
        final float floor = 130;
        if (luminance >= floor) return rgb;
        float t = (floor - luminance) / (255 - luminance);
        return Math.round(r + (255 - r) * t) << 16 | Math.round(g + (255 - g) * t) << 8 | Math.round(b + (255 - b) * t);
    }

    private static float luminance(int rgb) {
        return 0.299f * (rgb >> 16 & 0xFF) + 0.587f * (rgb >> 8 & 0xFF) + 0.114f * (rgb & 0xFF);
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
