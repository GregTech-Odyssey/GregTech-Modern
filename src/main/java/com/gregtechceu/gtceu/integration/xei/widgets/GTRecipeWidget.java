package com.gregtechceu.gtceu.integration.xei.widgets;

import com.gregtechceu.gtceu.api.gui.WidgetUtils;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.content.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.ContentRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.api.recipe.ui.RecipeInfoBuilder;
import com.gregtechceu.gtceu.api.recipe.ui.RecipeTierPreview;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.SlotGrid;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeInfoLines;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeSpecPanel;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeTierChip;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.GradientUtil;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.ColorUtils;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLLoader;

import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.GTValues.*;

/**
 * 配方查看器（EMI）里的一页配方，用新式界面框架（uipro）搭成，纯客户端控件
 *
 * 外框由 {@link PageFrame} 决定：最小宽度、要填满的高度、缺口里有几个按钮、是否画卡片。尺寸不必建控件就能算出（{@link #getPageSize}）。
 * 页面没有服务端（{@link ILocalUI}）：参数表、步进器的"服务端"取值与回调都在本端执行。
 */
public class GTRecipeWidget extends UIElement implements ILocalUI {

    private static final String DURATION = "gtceu.recipe.info.duration";
    private static final String SECONDS = "gtceu.recipe.info.seconds";
    private static final String PREVIEW_TIER = "gtceu.recipe.info.preview_tier";

    private static final int INLINE_SLOT_GAP = 6;

    // ==================== 卡片与缺口的几何 ====================
    /** 卡片外缘比页面四周多出的宽度（配方查看器在页面外留有这么宽的边）。 */
    public static final int CARD_MARGIN = 4;
    /** 配方查看器侧边按钮：边长与竖排间距。 */
    public static final int SIDE_BUTTON_SIZE = 12;
    public static final int SIDE_BUTTON_PITCH = 14;
    /** 侧边按钮左缘在页面右缘内侧多远：按钮右缘与卡片外缘齐。 */
    public static final int SIDE_BUTTON_INSET = SIDE_BUTTON_SIZE - CARD_MARGIN;

    /**
     * 配方页外框。
     *
     * @param minWidth    页面最小宽度（槽位区更宽时随槽位区）
     * @param fillHeight  页面要填满的高度，内容更高时随内容；0 为按内容
     * @param sideButtons 右下角缺口里竖排的按钮数，0 为不挖缺口；按钮由配方查看器自己画在
     *                    {@code x = 页宽 - SIDE_BUTTON_INSET}、自页面底边向上排，页面只留出位置
     * @param card        是否在页面四周画卡片（外扩 {@link #CARD_MARGIN}），代替配方查看器的默认底框
     */
    public record PageFrame(int minWidth, int fillHeight, int sideButtons, boolean card, int maxHeight) {

        public PageFrame(int minWidth, int fillHeight, int sideButtons, boolean card) {
            this(minWidth, fillHeight, sideButtons, card, 0);
        }

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
    @Nullable
    private final RecipeTierPreview preview;
    private final int minTier;
    private int tier;
    private boolean perfectOverclock;
    private Component durationText = Component.empty();
    private Component[] rowTexts = new Component[0];
    /// 各槽位对应的配方内容，与带内容的槽位（切换电压档时只刷新它们）
    private final Table<IO, RecipeInfo, ContentList> contents = Tables.newCustomTable(new EnumMap<>(IO.class), Reference2ReferenceLinkedOpenHashMap::new);
    private final List<ContentSlot> contentSlots = new ArrayList<>();

    public GTRecipeWidget(GTRecipeDefinition recipe) {
        this(recipe, PageFrame.COMPACT);
    }

    public GTRecipeWidget(GTRecipeDefinition recipe, PageFrame frame) {
        this.recipe = recipe;
        this.frame = frame;
        this.preview = RecipeTierPreview.create(recipe);
        this.minTier = preview == null ? recipe.tier : preview.minTier();
        this.tier = minTier;
        var content = contentSize(recipe, frame);
        int growth = stageGrowth(recipe, frame, content.height);
        var size = new Size(content.width, Math.max(content.height + growth, frame.fillHeight()));
        layout(l -> l.column().size(size.width, size.height).gapAll(UISizes.SECTION_GAP));
        setClientSideWidget();
        refreshPreview();

        var info = new RecipeInfoLines();
        appendInfo(recipe, info, this, preview);
        boolean inline = inlineSlots(recipe.recipeType.getRecipeUI().getSlotAreaSize(recipe).width, info.slots().size(), size.width);

        var stage = new UIElement().layout(l -> l.column().flexGrow(1).alignCenter().justifyContent(AlignContent.CENTER)
                .paddingHorizontal(UISizes.PANEL_PADDING));
        stage.setBackground(UITheme.PANEL);
        if (inline) {
            var row = new UIElement().layout(l -> l.row().gapAll(INLINE_SLOT_GAP).alignCenter());
            row.addChild(SlotGrid.of(info.slots().size(), createSlots(info)));
            row.addChild(createSlotArea(growth));
            stage.addChild(row);
        } else {
            stage.addChild(createSlotArea(growth));
        }
        if (hasIdButton()) {
            var id = new UIElement().layout(l -> l.positionType(TaffyPosition.ABSOLUTE).right(1).top(1));
            id.addChild(new IdLink());
            stage.addChild(id);
        }
        addChild(stage);

        var left = new UIElement().layout(l -> l.column().flexGrow(1).flexShrink(1).minWidth(0).gapAll(UISizes.SECTION_GAP));
        var panel = createPanel(info, frame.besideNotch(size.width));
        if (panel != null) left.addChild(panel);
        var footer = createFooter(inline ? new RecipeInfoLines() : info, size.width);
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
        var content = contentSize(recipe, frame);
        int height = content.height + stageGrowth(recipe, frame, content.height);
        return new Size(content.width, Math.max(height, frame.fillHeight()));
    }

    private static int stageGrowth(GTRecipeDefinition recipe, PageFrame frame, int contentHeight) {
        if (frame.maxHeight() <= contentHeight) return 0;
        return Math.min(frame.maxHeight() - contentHeight, recipe.recipeType.getRecipeUI().getSlotAreaOverflow(recipe));
    }

    private static Size contentSize(GTRecipeDefinition recipe, PageFrame frame) {
        var slotArea = recipe.recipeType.getRecipeUI().getSlotAreaSize(recipe);
        // 宽度取偶数：配方查看器会把页面宽度补成偶数，奇数宽的卡片会偏 1 像素
        int width = Math.max(slotArea.width + 2 * UISizes.PANEL_PADDING, frame.minWidth());
        width += width & 1;
        var counter = new RecipeInfoLines.Counter();
        var preview = RecipeTierPreview.create(recipe);
        appendInfo(recipe, counter, null, preview);
        boolean inline = inlineSlots(slotArea.width, counter.slots(), width);
        int stage = inline ? Math.max(slotArea.height, UISizes.SLOT_SIZE) : slotArea.height;
        int left = panelHeight(preview != null && preview.hasStepper(), counter.labeled(), counter.sentenceRows(frame.besideNotch(width)));
        int footer = footerHeight(inline ? 0 : counter.slots(), frame.besideNotch(width));
        if (footer > 0) left += (left > 0 ? UISizes.SECTION_GAP : 0) + footer;
        int lower = Math.max(left, frame.notchHeight());
        int height = stage + (lower > 0 ? UISizes.SECTION_GAP + lower : 0);
        return new Size(width, height);
    }

    private static boolean inlineSlots(int slotAreaWidth, int slots, int width) {
        return slots > 0 && slotAreaWidth + INLINE_SLOT_GAP + slots * UISizes.SLOT_SIZE <= width - 2 * UISizes.PANEL_PADDING;
    }

    /** 参数表高度；没有任何内容时为 0（不显示）。 */
    private static int panelHeight(boolean stepper, int values, int sentences) {
        if (stepper) values++;
        if (values + sentences == 0) return 0;
        return RecipeSpecPanel.heightFor(false, values, sentences);
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
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, rightBottom - 1, UITheme.CARD_FILL);
        graphics.fill(x0 + 1, y0 + 1, bottomRight - 1, y1 - 1, UITheme.CARD_FILL);
        // 描边
        graphics.fill(x0 + 1, y0, x1 - 1, y0 + 1, UITheme.CARD_OUTLINE);
        graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, UITheme.CARD_OUTLINE);
        graphics.fill(x1 - 1, y0 + 1, x1, rightBottom - 1, UITheme.CARD_OUTLINE);
        graphics.fill(x0 + 1, y1 - 1, bottomRight - 1, y1, UITheme.CARD_OUTLINE);
        if (notch) {
            graphics.fill(notchX - 1, notchY - 1, x1 - 1, notchY, UITheme.CARD_OUTLINE);
            graphics.fill(notchX - 1, notchY, notchX, y1 - 1, UITheme.CARD_OUTLINE);
        }
        // 左上高光
        graphics.fill(x0 + 1, y0 + 1, x1 - 3, y0 + 3, UITheme.CARD_HIGHLIGHT);
        graphics.fill(x0 + 1, y0 + 3, x0 + 3, y1 - 3, UITheme.CARD_HIGHLIGHT);
        // 右下阴影
        graphics.fill(x1 - 3, y0 + 3, x1 - 1, rightBottom - 1, UITheme.CARD_SHADOW);
        graphics.fill(x0 + 3, y1 - 3, bottomRight - 1, y1 - 1, UITheme.CARD_SHADOW);
        if (notch) {
            graphics.fill(notchX - 3, notchY - 3, x1 - 1, notchY - 1, UITheme.CARD_SHADOW);
            graphics.fill(notchX - 3, notchY - 1, notchX - 1, y1 - 1, UITheme.CARD_SHADOW);
        }
    }

    // ==================== 槽位区 ====================

    private Widget createSlotArea(int growth) {
        var slotArea = createSlotTemplate(recipe, contents);
        for (var widget : slotArea.getContainedWidgets(true)) {
            if (growth <= 0) break;
            if (widget instanceof ScrollerView scroller) growth -= scroller.growAdaptiveHeight(growth);
        }
        collectContentSlots(slotArea, contents, contentSlots);
        applyContentInfo();
        return slotArea;
    }

    public static List<Widget> createInfoSlots(GTRecipeDefinition recipe) {
        var info = new RecipeInfoLines();
        appendInfo(recipe, info, null, RecipeTierPreview.create(recipe));
        var slots = createSlots(info);
        for (var slot : slots) slot.setClientSideWidget();
        return slots;
    }

    private static WidgetGroup createSlotTemplate(GTRecipeDefinition recipe, Table<IO, RecipeInfo, ContentList> contents) {
        var storages = Tables.newCustomTable(new EnumMap<>(IO.class), Reference2ReferenceLinkedOpenHashMap<RecipeInfo, Object>::new);
        collectStorages(storages, contents, recipe);
        return recipe.recipeType.getRecipeUI().createRecipeTemplate(recipe, storages);
    }

    public void collectStorage(Table<IO, RecipeInfo, Object> extraTable,
                               Table<IO, RecipeInfo, ContentList> extraContents, GTRecipeDefinition recipe) {
        collectStorages(extraTable, extraContents, recipe);
    }

    private static void collectStorages(Table<IO, RecipeInfo, Object> extraTable,
                                        Table<IO, RecipeInfo, ContentList> extraContents, GTRecipeDefinition recipe) {
        collectStorage(extraTable, extraContents, recipe, IO.IN, ItemRecipeInfo.INSTANCE, displayItemInputs(recipe));
        collectStorage(extraTable, extraContents, recipe, IO.IN, FluidRecipeInfo.INSTANCE, recipe.fluidInputs);
        collectStorage(extraTable, extraContents, recipe, IO.OUT, ItemRecipeInfo.INSTANCE, recipe.itemOutputs);
        collectStorage(extraTable, extraContents, recipe, IO.OUT, FluidRecipeInfo.INSTANCE, recipe.fluidOutputs);
    }

    private static ContentList displayItemInputs(GTRecipeDefinition recipe) {
        var inputs = recipe.itemInputs;
        if (!recipe.recipeType.getRecipeUI().getSlotLayout().fitsRecipe()) return inputs;
        boolean sorted = true;
        for (int i = 1; i < inputs.size() && sorted; i++) sorted = inputOrder(inputs, i - 1) <= inputOrder(inputs, i);
        if (sorted) return inputs;
        var result = new ContentList.Builder(inputs.size());
        for (int order = 0; order <= 2; order++) {
            for (int i = 0; i < inputs.size(); i++) {
                if (inputOrder(inputs, i) == order) result.add(inputs.ingredient(i), inputs.amount(i), inputs.chance(i), inputs.boost(i), inputs.rollUnit(i));
            }
        }
        return result.build();
    }

    private static int inputOrder(ContentList inputs, int i) {
        if (inputs.chance(i) > 0) return 2;
        return inputs.ingredient(i).kind == KeyIngredient.CIRCUIT ? 0 : 1;
    }

    private static void collectStorage(Table<IO, RecipeInfo, Object> extraTable, Table<IO, RecipeInfo, ContentList> extraContents,
                                       GTRecipeDefinition recipe, IO io, ContentRecipeInfo cap, ContentList contents) {
        if (contents.isEmpty()) return;
        extraContents.put(io, cap, contents);
        List<Object> entries = cap.createXEIContainerContents(contents, recipe, io);
        int max = io == IO.IN ? recipe.recipeType.getMaxInputs(cap) : recipe.recipeType.getMaxOutputs(cap);
        while (entries.size() < max) entries.add(null);
        var container = cap.createXEIContainer(entries);
        if (container != null) extraTable.put(io, cap, container);
    }

    /** 记下有配方内容的槽位，切换电压档时只刷新它们的概率角标与提示，不重建控件。 */
    private record ContentSlot(Widget widget, IO io, ContentRecipeInfo cap, ContentList contents, int index) {}

    private static void collectContentSlots(WidgetGroup slotArea, Table<IO, RecipeInfo, ContentList> contents, List<ContentSlot> contentSlots) {
        for (var ioEntry : contents.rowMap().entrySet()) {
            var io = ioEntry.getKey();
            for (var capEntry : ioEntry.getValue().entrySet()) {
                if (!(capEntry.getKey() instanceof ContentRecipeInfo cap) || cap.getWidgetClass() == null) continue;
                var capContents = capEntry.getValue();
                WidgetUtils.indexedWidgetForEach(slotArea, cap.slotName(io), cap.getWidgetClass(), (widget, index) -> {
                    if (index < capContents.size()) contentSlots.add(new ContentSlot(widget, io, cap, capContents, index));
                });
            }
        }
    }

    /** 把每个槽位对应的配方内容（概率、消耗说明、角标）按当前电压档应用到槽位上。 */
    private void applyContentInfo() {
        applyContentInfo(contentSlots, recipe, minTier, tier);
    }

    private static void applyContentInfo(List<ContentSlot> contentSlots, GTRecipeDefinition recipe, int minTier, int tier) {
        for (var slot : contentSlots) {
            var contents = slot.contents();
            int i = slot.index();
            slot.cap().applyWidgetInfo(slot.widget(), i, true, slot.io(), null, recipe.recipeType, recipe, contents, i, null, minTier, tier);
            long fluidAmount = contents.ingredient(i).isFluid() ? contents.amount(i) : -1;
            slot.widget().setOverlay(contentOverlay(contents.chance(i), contents.boost(i), fluidAmount, minTier, tier, recipe.chanceFunction));
        }
    }

    // ==================== 信息区 ====================

    /**
     * 往信息区追加全部内容（{@code page} 为 null 时只用于数数）：核心参数、配方类型的数据行、扩展、条件、修饰器、配方类型的附加内容。
     */
    private static void appendInfo(GTRecipeDefinition recipe, RecipeInfoBuilder info, @Nullable GTRecipeWidget page,
                                   @Nullable RecipeTierPreview preview) {
        if (!recipe.data.getBoolean(GTRecipeDataKeys.HIDE_DURATION)) previewLine(info, DURATION, () -> page.durationText);
        if (preview != null) {
            var labels = preview.rowLabels();
            for (int i = 0; i < labels.size(); i++) {
                int row = i;
                previewLine(info, labels.get(i), () -> page.rowTexts[row]);
            }
        }
        for (var dataInfo : recipe.recipeType.getDataInfos()) {
            // 与电压档无关，取一次
            var text = dataInfo.apply(recipe);
            if (text.isBlank()) continue;
            info.sentence(Component.literal(text));
        }
        for (var extension : recipe.recipeExtensions) {
            if (preview == null || !preview.replaces(extension)) extension.appendInfo(recipe, info);
        }
        for (var extension : recipe.tickRecipeExtensions) {
            if (preview == null || !preview.replaces(extension)) extension.appendInfo(recipe, info);
        }
        for (var condition : recipe.conditions) condition.appendInfo(recipe, info);
        for (var modifier : recipe.recipeModifiers) modifier.appendInfo(recipe, info);
        recipe.recipeType.getRecipeUI().appendInfo(recipe, info);
    }

    /** 参数表：电压档预览（有耗电时）、核心参数与数据行，再是整句说明；什么都没有时返回 null。 */
    @Nullable
    private Widget createPanel(RecipeInfoLines info, int panelWidth) {
        boolean stepper = preview != null && preview.hasStepper();
        if (!stepper && info.lines().isEmpty()) return null;
        var panel = new RecipeSpecPanel();
        panel.layout(l -> l.flexGrow(1));
        if (stepper) {
            var chip = new RecipeTierChip(() -> tier, this::setTier, preview.minTier(), preview.maxTier(), this::tierName, preview.tooltip());
            panel.control(Component.translatable(PREVIEW_TIER), chip);
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
        var sentenceLines = new ArrayList<RecipeInfoLines.Line>();
        for (var line : info.lines()) if (line.labelKey() == null) sentenceLines.add(line);
        for (int i = 0; i < sentenceLines.size(); i++) {
            var line = sentenceLines.get(i);
            var next = i + 1 < sentenceLines.size() ? sentenceLines.get(i + 1) : null;
            if (line.onClick() != null) {
                panel.link(line.value(), line.onClick());
            } else if (next != null && next.onClick() == null && RecipeSpecPanel.fitsPair(line.value().get(), next.value().get(), panelWidth)) {
                panel.sentences(line.value(), next.value());
                i++;
            } else {
                panel.sentence(line.value());
            }
        }
        return panel;
    }

    /** 随超频预览变化的一行：建页时取页面缓存的文字（切换电压档时重算），数数时只计数。 */
    private static void previewLine(RecipeInfoBuilder info, String labelKey, Supplier<Component> value) {
        if (info instanceof RecipeInfoLines lines) lines.dynamicLine(labelKey, value);
        else info.line(labelKey, value);
    }

    // ==================== 底栏：额外展示槽 ====================

    private static boolean hasIdButton() {
        return !FMLLoader.isProduction();
    }

    private static int slotsPerRow(int width) {
        return Math.max(1, (width - UISizes.GAP) / UISizes.SLOT_SIZE);
    }

    private static int footerHeight(int slots, int width) {
        int perRow = slotsPerRow(width);
        return slots == 0 ? 0 : (slots + perRow - 1) / perRow * UISizes.SLOT_SIZE;
    }

    @Nullable
    private Widget createFooter(RecipeInfoLines info, int width) {
        if (info.slots().isEmpty()) return null;
        var footer = new UIElement().layout(l -> l.row().gapAll(UISizes.GAP).alignItems(AlignItems.END));
        footer.addChild(SlotGrid.of(slotsPerRow(frame.besideNotch(width)), createSlots(info)));
        return footer;
    }

    private static List<Widget> createSlots(RecipeInfoLines info) {
        var slots = new ArrayList<Widget>(info.slots().size());
        for (var slot : info.slots()) slots.add(slot.get());
        return slots;
    }

    private final class IdLink extends UIElement {

        private static final String TEXT = "ID";

        private IdLink() {
            layout(l -> l.size(Minecraft.getInstance().font.width(TEXT) + 2, UISizes.TEXT_HEIGHT + 1));
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            var font = Minecraft.getInstance().font;
            boolean hovered = isMouseOverElement(mouseX, mouseY);
            int color = hovered ? UITheme.LINK_TEXT : UITheme.PLACEHOLDER_TEXT;
            int x = getPositionX() + 1, y = getPositionY() + 1;
            graphics.drawString(font, TEXT, x, y, color, false);
            if (hovered) graphics.fill(x, y + 8, x + font.width(TEXT), y + 9, color);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
            if (gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
            gui.getModularUIGui().setHoverTooltip(List.of(Component.literal("click to copy: " + recipe.id)), ItemStack.EMPTY, null, null);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !isMouseOverElement(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
            playButtonClickSound();
            Minecraft.getInstance().keyboardHandler.setClipboard(recipe.id.toString());
            return true;
        }
    }

    // ==================== 超频预览 ====================

    private void setTier(int tier) {
        if (preview == null) return;
        this.tier = Math.clamp(tier, preview.minTier(), preview.maxTier());
        this.perfectOverclock = preview.supportsPerfect() && GTUtil.isShiftDown();
        refreshPreview();
        applyContentInfo();
    }

    private Component tierName(int tier) {
        var name = preview.tierName(tier);
        return perfectOverclock && tier == this.tier && tier > minTier ? name.copy().append("*") : name;
    }

    private void refreshPreview() {
        int duration = preview == null ? recipe.duration : preview.duration(tier, perfectOverclock);
        durationText = Component.translatable(SECONDS, FormattingUtil.formatNumbers(duration / 20f));
        if (preview == null) return;
        int rows = preview.rowLabels().size();
        if (rowTexts.length != rows) rowTexts = new Component[rows];
        for (int i = 0; i < rows; i++) rowTexts[i] = preview.rowValue(i, tier, perfectOverclock);
    }

    // ==================== 公共工具 ====================

    public static void setConsumedChance(int chance, int boost, List<Component> tooltips, int recipeTier,
                                         int chanceTier, ChanceBoostFunction function) {
        if (chance < ContentList.MAX_CHANCE) {
            int boostedChance = function.getBoostedChance(chance, boost, recipeTier, chanceTier);
            if (boostedChance == 0) {
                tooltips.add(Component.translatable("gtceu.gui.content.chance_nc"));
            } else {
                float baseChanceFloat = 100f * chance / ContentList.MAX_CHANCE;
                float boostedChanceFloat = 100f * boostedChance / ContentList.MAX_CHANCE;
                tooltips.add(FormattingUtil.formatPercentage2Places("gtceu.gui.content.chance_base", baseChanceFloat));
                if (boost != 0) {
                    String key = boost > 0 ? "gtceu.gui.content.chance_tier_boost_plus" : "gtceu.gui.content.chance_tier_boost_minus";
                    tooltips.add(FormattingUtil.formatPercentage2Places(key, Math.abs(100f * boost / ContentList.MAX_CHANCE)));
                }
                tooltips.add(FormattingUtil.formatPercentage2Places("gtceu.gui.content.chance_boosted", boostedChanceFloat));
            }
        }
    }

    public static IGuiTexture contentOverlay(int chance, int boost, long fluidAmount, int recipeTier, int chanceTier, @Nullable ChanceBoostFunction function) {
        return new ContentOverlay(chance, boost, fluidAmount, recipeTier, chanceTier, function == null ? ChanceBoostFunction.NONE : function);
    }

    private record ContentOverlay(int chance, int boost, long fluidAmount, int recipeTier, int chanceTier, ChanceBoostFunction function) implements IGuiTexture {

        @Override
        @OnlyIn(Dist.CLIENT)
        public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
            drawChance(graphics, x, y, width, height);
            if (fluidAmount >= 0) drawFluidAmount(graphics, x, y, width, height);
        }

        @OnlyIn(Dist.CLIENT)
        private void drawFluidAmount(GuiGraphics graphics, float x, float y, int width, int height) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            graphics.pose().scale(0.5F, 0.5F, 1);
            Font fontRenderer = Minecraft.getInstance().font;
            String s = FormattingUtil.formatBuckets(fluidAmount);
            if (fontRenderer.width(s) > 32) s = FormattingUtil.formatNumberReadable(fluidAmount, true, FormattingUtil.DECIMAL_FORMAT_1F, "B");
            if (fontRenderer.width(s) > 32) s = FormattingUtil.formatNumberReadable(fluidAmount, true, FormattingUtil.DECIMAL_FORMAT_0F, "B");
            graphics.drawString(fontRenderer, s, (int) ((x + (width / 3.0F)) * 2 - fontRenderer.width(s) + 22), (int) ((y + (height / 3.0F) + 6) * 2), 16777215, true);
            graphics.pose().popPose();
        }

        @OnlyIn(Dist.CLIENT)
        private void drawChance(GuiGraphics graphics, float x, float y, int width, int height) {
            if (chance == ContentList.MAX_CHANCE) return;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            graphics.pose().scale(0.5F, 0.5F, 1);
            int boosted = function.getBoostedChance(chance, boost, recipeTier, chanceTier);
            float chanceFloat = 1.0F * boosted / ContentList.MAX_CHANCE;
            String percent = FormattingUtil.formatNumber2Places(100 * chanceFloat);
            String s = boosted == 0 ? LocalizationUtils.format("gtceu.gui.content.chance_nc_short") : percent + "%";
            int color = boosted == 0 ? 16711680 : GradientUtil.toRGB(Mth.lerp(chanceFloat, 29.0F, 167.0F), 100.0F, 50.0F);
            Font fontRenderer = Minecraft.getInstance().font;
            graphics.drawString(fontRenderer, s, (int) ((x + (width / 3.0F)) * 2 - fontRenderer.width(s) + 23), (int) ((y + (height / 3.0F) + 6) * 2 - height), color(color), true);
            graphics.pose().popPose();
        }

        @OnlyIn(Dist.CLIENT)
        private static int color(int color) {
            if (color != 0xFF0000) return color;
            double progress = Math.abs(System.currentTimeMillis() % 4000) / 4000.0d;
            float alpha = (float) ((Math.cos(progress * 2 * Math.PI) + 1) / 2.2 + 0.05);
            return ColorUtils.color(alpha, 1f, 0.0f, 0.0f);
        }
    }

    /** 本配方页对应的配方。 */
    public GTRecipeDefinition getRecipe() {
        return recipe;
    }
}
