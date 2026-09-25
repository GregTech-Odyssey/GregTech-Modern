package com.gregtechceu.gtceu.integration.xei.widgets;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.WidgetUtils;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.content.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.content.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.ContentRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.api.recipe.ui.RecipeInfoBuilder;
import com.gregtechceu.gtceu.api.recipe.ui.RecipeSlotLayouts;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.InfoIcon;
import com.gregtechceu.gtceu.uipro.elements.Stepper;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeInfoLines;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeSpecPanel;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLLoader;

import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.GTValues.*;

/**
 * 配方查看器（EMI）里的一页配方，用新式界面框架（uipro）搭成，纯客户端控件：
 *
 * <pre>
 *   ┌──────────────────────────────────────┐
 *   │ ┌──────────────────────────────────┐ │
 *   │ │     [输入] ──进度──▶ [输出]        │ │  舞台：槽位区（配方类型的排布）居中，吃掉剩余高度
 *   │ └──────────────────────────────────┘ │
 *   │ ┌ 超频预览 ──────── (i) [&lt; LV &gt;] ┐    │  参数表：电压档预览、核心参数、配方数据与条件
 *   │ │ 耗时 ……………………………… 21.9 秒 │  ▢ │
 *   │ │ 耗能功率 …… 30 EU/t          │  ▢ │  右下角缺口：配方查看器自己的按钮（填充配方、合成树、默认配方……）
 *   │ └─────────────────────────────┘  ▢ │
 *   │ [线圈] [维度]               [ID]     │  底栏：额外展示槽 / 开发环境复制 ID
 *   └───────────────────────────────┘
 * </pre>
 *
 * 外框由 {@link PageFrame} 决定：最小宽度、要填满的高度、缺口里有几个按钮、是否画卡片。尺寸不必建控件就能算出（{@link #getPageSize}）。
 * 页面没有服务端（{@link ILocalUI}）：参数表、步进器的"服务端"取值与回调都在本端执行。
 */
public class GTRecipeWidget extends UIElement implements ILocalUI {

    private static final String DURATION = "gtceu.recipe.info.duration";
    private static final String SECONDS = "gtceu.recipe.info.seconds";
    private static final String TOTAL_EU = "gtceu.recipe.info.total_eu";
    private static final String MAX_EU = "gtceu.recipe.info.max_eu";
    private static final String EU_USAGE = "gtceu.recipe.info.eu_usage";
    private static final String EU_GENERATION = "gtceu.recipe.info.eu_generation";
    private static final String AMPERAGE = "gtceu.recipe.info.amperage";
    private static final String OVERCLOCK_INFO = "gtceu.recipe.info.overclock";
    private static final String OVERCLOCK_PERFECT = "gtceu.recipe.info.overclock_perfect";
    private static final String PREVIEW_TIER = "gtceu.recipe.info.preview_tier";

    /** 电压档步进器中间数值框的宽度。 */
    private static final int TIER_VALUE_WIDTH = UISizes.VALUE_WIDTH;

    // ==================== 卡片与缺口的几何 ====================
    /** 卡片外缘比页面四周多出的宽度（配方查看器在页面外留有这么宽的边）。 */
    public static final int CARD_MARGIN = 4;
    /** 配方查看器侧边按钮：边长与竖排间距。 */
    public static final int SIDE_BUTTON_SIZE = 12;
    public static final int SIDE_BUTTON_PITCH = 14;
    /** 侧边按钮左缘在页面右缘内侧多远：按钮右缘与卡片外缘齐。 */
    public static final int SIDE_BUTTON_INSET = SIDE_BUTTON_SIZE - CARD_MARGIN;
    /// 卡片：原版窗口的描边、高光、阴影与底色（与配方查看器页面一致）
    private static final int CARD_OUTLINE = 0xFF000000;
    private static final int CARD_LIGHT = 0xFFFFFFFF;
    private static final int CARD_SHADOW = 0xFF555555;
    private static final int CARD_FILL = 0xFFC6C6C6;

    /**
     * 配方页外框。
     *
     * @param minWidth    页面最小宽度（槽位区更宽时随槽位区）
     * @param fillHeight  页面要填满的高度，内容更高时随内容；0 为按内容
     * @param sideButtons 右下角缺口里竖排的按钮数，0 为不挖缺口；按钮由配方查看器自己画在
     *                    {@code x = 页宽 - SIDE_BUTTON_INSET}、自页面底边向上排，页面只留出位置
     * @param card        是否在页面四周画卡片（外扩 {@link #CARD_MARGIN}），代替配方查看器的默认底框
     */
    public record PageFrame(int minWidth, int fillHeight, int sideButtons, boolean card) {

        /** 按内容大小、不挖缺口、不画卡片。 */
        public static final PageFrame COMPACT = new PageFrame(UISizes.CONTENT_WIDTH, 0, 0, false);
        /// 缺口在页面内的宽度：按钮左侧留 2 像素（缺口上方与最上面的按钮之间也是 2 像素，即行距 14 − 按钮 12）
        public static final int NOTCH_WIDTH = SIDE_BUTTON_INSET + 2;

        /** 缺口在页面内的高度：按钮竖排的总高（最下面的按钮底边与页面底边齐），加上方 2 像素，正好装下按钮。 */
        public int notchHeight() {
            return sideButtons * SIDE_BUTTON_PITCH;
        }

        /** 下半部左侧（参数表、底栏）的宽度：挖缺口时让出缺口和间距。 */
        public int besideNotch(int width) {
            return sideButtons > 0 ? width - NOTCH_WIDTH - UISizes.SECTION_GAP : width;
        }
    }

    private final GTRecipeDefinition recipe;
    private final PageFrame frame;
    private final int minTier;
    /// 超频预览：当前电压档、是否按无损超频计算
    private int tier;
    private boolean perfectOverclock;
    /// 当前电压档下各行的显示值，只在切换电压档时重算（参数表每帧取值）
    private Component durationText = Component.empty();
    private Component energyText = Component.empty();
    private Component powerText = Component.empty();
    private Component amperageText = Component.empty();
    private String tierText = "";
    /// 各槽位对应的配方内容，与带内容的槽位（切换电压档时只刷新它们）
    private final Table<IO, RecipeInfo, List<Content>> contents = Tables.newCustomTable(new EnumMap<>(IO.class), Reference2ReferenceLinkedOpenHashMap::new);
    private final List<Widget> contentSlots = new ArrayList<>();

    public GTRecipeWidget(GTRecipeDefinition recipe) {
        this(recipe, PageFrame.COMPACT);
    }

    public GTRecipeWidget(GTRecipeDefinition recipe, PageFrame frame) {
        this.recipe = recipe;
        this.frame = frame;
        this.minTier = recipe.tier;
        this.tier = recipe.tier;
        var size = getPageSize(recipe, frame);
        layout(l -> l.column().size(size.width, size.height).gapAll(UISizes.SECTION_GAP));
        setClientSideWidget();
        refreshPreview();

        var stage = new UIElement().layout(l -> l.column().flexGrow(1).alignCenter().justifyContent(AlignContent.CENTER)
                .paddingHorizontal(UITheme.PANEL_PADDING));
        stage.setBackground(UITheme.PANEL);
        stage.addChild(createSlotArea());
        addChild(stage);

        var info = new RecipeInfoLines();
        appendInfo(recipe, info, this);
        var left = new UIElement().layout(l -> l.column().flexGrow(1).flexShrink(1).minWidth(0).gapAll(UISizes.SECTION_GAP));
        var panel = createPanel(info);
        if (panel != null) left.addChild(panel);
        var footer = createFooter(info, size.width);
        if (footer != null) left.addChild(footer);
        if (panel == null && footer == null && frame.sideButtons() == 0) return;
        var lower = new UIElement().layout(l -> l.row().gapAll(UISizes.SECTION_GAP).minHeight(frame.notchHeight()));
        lower.addChild(left);
        if (frame.sideButtons() > 0) lower.addChild(UIElement.spacer(PageFrame.NOTCH_WIDTH, 0));
        addChild(lower);
    }

    // ==================== 尺寸 ====================

    /** 配方页的尺寸，不建控件：舞台（槽位区每个配方类型量一次）+ 参数表行数 + 底栏，再按外框放大。 */
    public static Size getPageSize(GTRecipeDefinition recipe, PageFrame frame) {
        var slotArea = recipe.recipeType.getRecipeUI().getSlotAreaSize();
        // 宽度取偶数：配方查看器会把页面宽度补成偶数，奇数宽的卡片会偏 1 像素
        int width = Math.max(slotArea.width + 2 * UITheme.PANEL_PADDING, frame.minWidth());
        width += width & 1;
        var counter = new RecipeInfoLines.Counter();
        appendInfo(recipe, counter, null);
        int stage = stageHeight(recipe);
        int left = panelHeight(recipe, counter.labeled(), counter.sentences());
        int footer = footerHeight(counter.slots(), frame.besideNotch(width), idInFooter(recipe));
        if (footer > 0) left += (left > 0 ? UISizes.SECTION_GAP : 0) + footer;
        int lower = Math.max(left, frame.notchHeight());
        int height = stage + (lower > 0 ? UISizes.SECTION_GAP + lower : 0);
        return new Size(width, Math.max(height, frame.fillHeight()));
    }

    private static int stageHeight(GTRecipeDefinition recipe) {
        return recipe.recipeType.getRecipeUI().getSlotAreaSize().height;
    }

    /** 参数表高度；没有任何内容时为 0（不显示）。 */
    private static int panelHeight(GTRecipeDefinition recipe, int values, int sentences) {
        boolean header = hasTierStepper(recipe);
        if (!header && values + sentences == 0) return 0;
        return RecipeSpecPanel.heightFor(header, values, sentences);
    }

    // ==================== 卡片 ====================

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawPageCard(graphics, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), frame);
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @OnlyIn(Dist.CLIENT)
    public static void drawPageCard(GuiGraphics graphics, int x, int y, int width, int height, PageFrame frame) {
        if (!frame.card()) return;
        int x1 = x + width + CARD_MARGIN, y1 = y + height + CARD_MARGIN;
        if (frame.sideButtons() > 0) {
            drawCard(graphics, x - CARD_MARGIN, y - CARD_MARGIN, x1, y1, x + width - PageFrame.NOTCH_WIDTH, y + height - frame.notchHeight());
        } else {
            drawCard(graphics, x - CARD_MARGIN, y - CARD_MARGIN, x1, y1, x1, y1);
        }
    }

    /**
     * 原版窗口样式（1 像素黑描边、左上 2 像素白高光、右下 2 像素灰阴影）的卡片，右下角挖去 {@code [notchX, x1) × [notchY, y1)}；
     * 不挖缺口时传 {@code notchX = x1, notchY = y1}。缺口的上边和左边按"卡片的下边、右边"画阴影和描边。
     */
    @OnlyIn(Dist.CLIENT)
    private static void drawCard(GuiGraphics graphics, int x0, int y0, int x1, int y1, int notchX, int notchY) {
        boolean notch = notchX < x1 && notchY < y1;
        // 右边、底边在缺口处的终点
        int rightBottom = notch ? notchY : y1;
        int bottomRight = notch ? notchX : x1;
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, rightBottom - 1, CARD_FILL);
        graphics.fill(x0 + 1, y0 + 1, bottomRight - 1, y1 - 1, CARD_FILL);
        // 描边
        graphics.fill(x0 + 1, y0, x1 - 1, y0 + 1, CARD_OUTLINE);
        graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, CARD_OUTLINE);
        graphics.fill(x1 - 1, y0 + 1, x1, rightBottom - 1, CARD_OUTLINE);
        graphics.fill(x0 + 1, y1 - 1, bottomRight - 1, y1, CARD_OUTLINE);
        if (notch) {
            graphics.fill(notchX - 1, notchY - 1, x1 - 1, notchY, CARD_OUTLINE);
            graphics.fill(notchX - 1, notchY, notchX, y1 - 1, CARD_OUTLINE);
        }
        // 左上高光
        graphics.fill(x0 + 1, y0 + 1, x1 - 3, y0 + 3, CARD_LIGHT);
        graphics.fill(x0 + 1, y0 + 3, x0 + 3, y1 - 3, CARD_LIGHT);
        // 右下阴影
        graphics.fill(x1 - 3, y0 + 3, x1 - 1, rightBottom - 1, CARD_SHADOW);
        graphics.fill(x0 + 3, y1 - 3, bottomRight - 1, y1 - 1, CARD_SHADOW);
        if (notch) {
            graphics.fill(notchX - 3, notchY - 3, x1 - 1, notchY - 1, CARD_SHADOW);
            graphics.fill(notchX - 3, notchY - 1, notchX - 1, y1 - 1, CARD_SHADOW);
        }
    }

    // ==================== 槽位区 ====================

    private Widget createSlotArea() {
        var storages = Tables.newCustomTable(new EnumMap<>(IO.class), Reference2ReferenceLinkedOpenHashMap<RecipeInfo, Object>::new);
        collectStorage(storages, contents, recipe);
        var slotArea = recipe.recipeType.getRecipeUI().createUITemplate(ProgressWidget.JEIProgress, storages,
                recipe.data.clone(), List.of(recipe.conditions));
        collectContentSlots(slotArea);
        applyContentInfo();
        return slotArea;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void collectStorage(Table<IO, RecipeInfo, Object> extraTable,
                               Table<IO, RecipeInfo, List<Content>> extraContents, GTRecipeDefinition recipe) {
        collectStorage(extraTable, extraContents, IO.IN, ItemRecipeInfo.INSTANCE, (List) recipe.itemInputs);
        collectStorage(extraTable, extraContents, IO.IN, FluidRecipeInfo.INSTANCE, (List) recipe.fluidInputs);
        collectStorage(extraTable, extraContents, IO.OUT, ItemRecipeInfo.INSTANCE, (List) recipe.itemOutputs);
        collectStorage(extraTable, extraContents, IO.OUT, FluidRecipeInfo.INSTANCE, (List) recipe.fluidOutputs);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void collectStorage(Table<IO, RecipeInfo, Object> extraTable, Table<IO, RecipeInfo, List<Content>> extraContents,
                                IO io, ContentRecipeInfo cap, List<Content> contents) {
        if (contents.isEmpty()) return;
        extraContents.put(io, cap, contents);
        List<Object> entries = cap.createXEIContainerContents(contents, recipe, io);
        int max = io == IO.IN ? recipe.recipeType.getMaxInputs(cap) : recipe.recipeType.getMaxOutputs(cap);
        while (entries.size() < max) entries.add(null);
        var container = cap.createXEIContainer(entries);
        if (container != null) extraTable.put(io, cap, container);
    }

    /** 记下有配方内容的槽位，切换电压档时只刷新它们的概率角标与提示，不重建控件。 */
    private void collectContentSlots(WidgetGroup slotArea) {
        for (var ioEntry : contents.rowMap().entrySet()) {
            for (var capEntry : ioEntry.getValue().entrySet()) {
                if (!(capEntry.getKey() instanceof ContentRecipeInfo<?, ?> cap) || cap.getWidgetClass() == null) continue;
                WidgetUtils.widgetByIdForEach(slotArea, "^%s_[0-9]+$".formatted(cap.slotName(ioEntry.getKey())), cap.getWidgetClass(), contentSlots::add);
            }
        }
    }

    /** 把每个槽位对应的配方内容（概率、消耗说明、角标）按当前电压档应用到槽位上。 */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void applyContentInfo() {
        for (var widget : contentSlots) {
            var id = widget.getId();
            for (var ioEntry : contents.rowMap().entrySet()) {
                var io = ioEntry.getKey();
                for (var capEntry : ioEntry.getValue().entrySet()) {
                    if (!(capEntry.getKey() instanceof ContentRecipeInfo cap) || !id.startsWith(cap.slotName(io) + "_")) continue;
                    int index = WidgetUtils.widgetIdIndex(widget);
                    List<Content> capContents = capEntry.getValue();
                    if (index < 0 || index >= capContents.size()) continue;
                    var content = capContents.get(index);
                    cap.applyWidgetInfo(widget, index, true, io, null, recipe.recipeType, recipe, content, null, minTier, tier);
                    widget.setOverlay(content.createOverlay(false, minTier, tier, recipe.chanceFunction));
                }
            }
        }
    }

    // ==================== 信息区 ====================

    /**
     * 往信息区追加全部内容（{@code page} 为 null 时只用于数数）：核心参数、配方类型的数据行、扩展、条件、修饰器、配方类型的附加内容。
     */
    private static void appendInfo(GTRecipeDefinition recipe, RecipeInfoBuilder info, @Nullable GTRecipeWidget page) {
        long inputEUt = recipe.getInputEUt();
        long outputEUt = recipe.getOutputEUt();
        long eut = inputEUt == 0 ? outputEUt : inputEUt;
        if (!recipe.data.getBoolean(GTRecipeDataKeys.HIDE_DURATION)) previewLine(info, DURATION, () -> page.durationText);
        if (eut > 0) {
            previewLine(info, isTotalCwu(recipe) ? MAX_EU : TOTAL_EU, () -> page.energyText);
            previewLine(info, inputEUt != 0 ? EU_USAGE : EU_GENERATION, () -> page.powerText);
            previewLine(info, AMPERAGE, () -> page.amperageText);
        }
        for (var dataInfo : recipe.recipeType.getDataInfos()) {
            // 与电压档无关，取一次
            var text = dataInfo.apply(recipe);
            if (text.isBlank()) continue;
            info.sentence(Component.literal(text));
        }
        for (var extension : recipe.recipeExtensions) extension.appendInfo(recipe, info);
        for (var extension : recipe.tickRecipeExtensions) extension.appendInfo(recipe, info);
        for (var condition : recipe.conditions) condition.appendInfo(recipe, info);
        for (var modifier : recipe.recipeModifiers) modifier.appendInfo(recipe, info);
        recipe.recipeType.getRecipeUI().appendInfo(recipe, info);
    }

    /** 参数表：电压档预览（有耗电时）、核心参数与数据行，再是整句说明；什么都没有时返回 null。 */
    @Nullable
    private Widget createPanel(RecipeInfoLines info) {
        boolean header = hasTierStepper(recipe);
        if (!header && info.lines().isEmpty()) return null;
        var panel = new RecipeSpecPanel();
        panel.layout(l -> l.flexGrow(1));
        if (header) {
            var overclockInfo = new InfoIcon(InfoIcon.Kind.INFO,
                    Component.translatable(OVERCLOCK_INFO, VNF[minTier]),
                    Component.translatable(OVERCLOCK_PERFECT).withStyle(ChatFormatting.GRAY));
            var stepper = new Stepper(TIER_VALUE_WIDTH, () -> tier, this::setTier, minTier, GTValues.MAX, false,
                    value -> value == tier ? tierText : VN[value]);
            if (hasIdButton()) panel.header(Component.translatable(PREVIEW_TIER), overclockInfo, stepper, createIdButton());
            else panel.header(Component.translatable(PREVIEW_TIER), overclockInfo, stepper);
        }
        boolean values = false, sentences = false;
        for (var line : info.lines()) {
            if (line.labelKey() == null) {
                sentences = true;
                continue;
            }
            values = true;
            panel.value(Component.translatable(line.labelKey()), line.value());
        }
        if (values && sentences) panel.divider();
        for (var line : info.lines()) {
            if (line.labelKey() == null) panel.sentence(line.value());
        }
        return panel;
    }

    /** 随超频预览变化的一行：建页时取页面缓存的文字（切换电压档时重算），数数时只计数。 */
    private static void previewLine(RecipeInfoBuilder info, String labelKey, Supplier<Component> value) {
        if (info instanceof RecipeInfoLines lines) lines.dynamicLine(labelKey, value);
        else info.line(labelKey, value);
    }

    /** 算力配方：耗时一栏代表总计算量，耗能按总算力折算成最多耗能。 */
    private static boolean isTotalCwu(GTRecipeDefinition recipe) {
        return recipe.data.getBoolean(GTRecipeDataKeys.DURATION_IS_TOTAL_CWU) && recipe.data.containsKey(GTRecipeDataKeys.CWUT);
    }

    // ==================== 底栏：额外展示槽 ====================

    private static boolean hasTierStepper(GTRecipeDefinition recipe) {
        return recipe.getInputEUt() > 0;
    }

    private static boolean hasIdButton() {
        return !FMLLoader.isProduction();
    }

    private static boolean idInFooter(GTRecipeDefinition recipe) {
        return hasIdButton() && !hasTierStepper(recipe);
    }

    /** 额外展示槽一行放几个：底栏是 {@code [槽网格][弹性空白][ID 按钮]}，相邻子元素之间各有一个 GAP。 */
    private static int slotsPerRow(int width, boolean idButton) {
        int available = width - UISizes.GAP - (idButton ? Button.ICON_SIZE + UISizes.GAP : 0);
        return Math.max(1, available / UISizes.SLOT);
    }

    private static int footerHeight(int slots, int width, boolean idButton) {
        int perRow = slotsPerRow(width, idButton);
        int rows = slots == 0 ? 0 : (slots + perRow - 1) / perRow;
        return Math.max(idButton ? UISizes.CONTROL_HEIGHT : 0, rows * UISizes.SLOT);
    }

    @Nullable
    private Widget createFooter(RecipeInfoLines info, int width) {
        boolean idButton = idInFooter(recipe);
        if (info.slots().isEmpty() && !idButton) return null;
        var footer = new UIElement().layout(l -> l.row().gapAll(UISizes.GAP).alignItems(AlignItems.END));
        if (!info.slots().isEmpty()) {
            var slots = new ArrayList<Widget>(info.slots().size());
            for (var slot : info.slots()) slots.add(slot.get());
            footer.addChild(RecipeSlotLayouts.grid(slots, slotsPerRow(frame.besideNotch(width), idButton)));
        }
        footer.addChild(UIElement.flexSpacer());
        if (idButton) footer.addChild(createIdButton());
        return footer;
    }

    private Widget createIdButton() {
        return Button.glyph("ID")
                .setOnClientClick(() -> Minecraft.getInstance().keyboardHandler.setClipboard(recipe.id.toString()))
                .setHoverTooltips(Component.literal("click to copy: " + recipe.id));
    }

    // ==================== 超频预览 ====================

    /** 切换电压档（纯客户端）。按住 Shift 切换时按无损超频计算耗时。 */
    private void setTier(int tier) {
        this.tier = Math.clamp(tier, minTier, GTValues.MAX);
        this.perfectOverclock = GTUtil.isShiftDown();
        refreshPreview();
        applyContentInfo();
    }

    /** 按当前电压档重算各行的显示值。 */
    private void refreshPreview() {
        int duration = previewDuration();
        long eut = previewEUt();
        durationText = Component.translatable(SECONDS, FormattingUtil.formatNumbers(duration / 20f));
        long energy = eut * duration;
        if (isTotalCwu(recipe)) energy /= Math.max(recipe.data.getLong(GTRecipeDataKeys.CWUT), 1);
        energyText = Component.literal(FormattingUtil.formatNumbers(energy) + " EU");
        powerText = Component.literal(FormattingUtil.formatNumbers(eut) + " EU/t");
        int voltageTier = GTUtil.getTierByVoltage(eut);
        amperageText = Component.translatable("gtceu.recipe.eu.tier", FormattingUtil.formatNumber2Places((float) eut / V[voltageTier]), VN[voltageTier]);
        // 超过最低电压且按无损超频计算时，档位后加 * 提示
        tierText = perfectOverclock && tier > minTier ? VN[tier] + "*" : VN[tier];
    }

    private int overclocks() {
        return recipe.getInputEUt() != 0 ? tier - minTier : 0;
    }

    /** 当前电压档下的耗时（tick）。 */
    private int previewDuration() {
        int ocs = overclocks();
        if (ocs <= 0) return recipe.duration;
        return Math.max(1, (int) (recipe.duration / Math.pow(perfectOverclock ? 4 : 2, ocs)));
    }

    /** 当前电压档下的功率。 */
    private long previewEUt() {
        long inputEUt = recipe.getInputEUt();
        if (inputEUt == 0) return recipe.getOutputEUt();
        return (long) (inputEUt * Math.pow(4, overclocks()));
    }

    // ==================== 公共工具 ====================

    public static void setConsumedChance(Content content, ChanceLogic logic, List<Component> tooltips, int recipeTier,
                                         int chanceTier, ChanceBoostFunction function) {
        if (content.chance < Content.MAX_CHANCE) {
            int boostedChance = function.getBoostedChance(content, recipeTier, chanceTier);
            if (boostedChance == 0) {
                tooltips.add(Component.translatable("gtceu.gui.content.chance_nc"));
            } else {
                float baseChanceFloat = 100f * content.chance / Content.MAX_CHANCE;
                float boostedChanceFloat = 100f * boostedChance / Content.MAX_CHANCE;
                if (logic != ChanceLogic.NONE && logic != ChanceLogic.OR) {
                    tooltips.add(Component.translatable("gtceu.gui.content.chance_base_logic",
                            FormattingUtil.formatNumber2Places(baseChanceFloat), logic.getTranslation())
                            .withStyle(ChatFormatting.YELLOW));
                } else {
                    tooltips.add(
                            FormattingUtil.formatPercentage2Places("gtceu.gui.content.chance_base", baseChanceFloat));
                }
                if (content.tierChanceBoost != 0) {
                    String key = "gtceu.gui.content.chance_tier_boost_" +
                            ((content.tierChanceBoost > 0) ? "plus" : "minus");
                    tooltips.add(FormattingUtil.formatPercentage2Places(key,
                            Math.abs(100f * content.tierChanceBoost / Content.MAX_CHANCE)));
                }
                if (logic != ChanceLogic.NONE && logic != ChanceLogic.OR) {
                    tooltips.add(Component.translatable("gtceu.gui.content.chance_boosted_logic",
                            FormattingUtil.formatNumber2Places(boostedChanceFloat), logic.getTranslation())
                            .withStyle(ChatFormatting.YELLOW));
                } else {
                    tooltips.add(
                            FormattingUtil.formatPercentage2Places("gtceu.gui.content.chance_boosted",
                                    boostedChanceFloat));
                }
            }
        }
    }

    /** 本配方页对应的配方。 */
    public GTRecipeDefinition getRecipe() {
        return recipe;
    }
}
