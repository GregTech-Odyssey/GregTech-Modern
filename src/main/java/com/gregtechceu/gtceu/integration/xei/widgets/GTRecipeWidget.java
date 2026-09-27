package com.gregtechceu.gtceu.integration.xei.widgets;

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
import com.gregtechceu.gtceu.api.recipe.ui.RecipeTierPreview;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeInfoLines;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeSpecPanel;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeTierChip;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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
    @Nullable
    private final RecipeTierPreview preview;
    private final int minTier;
    private int tier;
    private boolean perfectOverclock;
    private Component durationText = Component.empty();
    private Component[] rowTexts = new Component[0];
    /// 各槽位对应的配方内容，与带内容的槽位（切换电压档时只刷新它们）
    private final Table<IO, RecipeInfo, List<Content>> contents = Tables.newCustomTable(new EnumMap<>(IO.class), Reference2ReferenceLinkedOpenHashMap::new);
    private final List<Widget> contentSlots = new ArrayList<>();

    public GTRecipeWidget(GTRecipeDefinition recipe) {
        this(recipe, PageFrame.COMPACT);
    }

    public GTRecipeWidget(GTRecipeDefinition recipe, PageFrame frame) {
        this.recipe = recipe;
        this.frame = frame;
        this.preview = RecipeTierPreview.create(recipe);
        this.minTier = preview == null ? recipe.tier : preview.minTier();
        this.tier = minTier;
        var size = getPageSize(recipe, frame);
        layout(l -> l.column().size(size.width, size.height).gapAll(UISizes.SECTION_GAP));
        setClientSideWidget();
        refreshPreview();

        var info = new RecipeInfoLines();
        appendInfo(recipe, info, this, preview);
        boolean inline = inlineSlots(recipe.recipeType.getRecipeUI().getSlotAreaSize(recipe).width, info.slots().size(), size.width);

        var stage = new UIElement().layout(l -> l.column().flexGrow(1).alignCenter().justifyContent(AlignContent.CENTER)
                .paddingHorizontal(UITheme.PANEL_PADDING));
        stage.setBackground(UITheme.PANEL);
        if (inline) {
            var row = new UIElement().layout(l -> l.row().gapAll(INLINE_SLOT_GAP).alignCenter());
            row.addChild(RecipeSlotLayouts.grid(createSlots(info), info.slots().size()));
            row.addChild(createSlotArea());
            stage.addChild(row);
        } else {
            stage.addChild(createSlotArea());
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
        var slotArea = recipe.recipeType.getRecipeUI().getSlotAreaSize(recipe);
        // 宽度取偶数：配方查看器会把页面宽度补成偶数，奇数宽的卡片会偏 1 像素
        int width = Math.max(slotArea.width + 2 * UITheme.PANEL_PADDING, frame.minWidth());
        width += width & 1;
        var counter = new RecipeInfoLines.Counter();
        var preview = RecipeTierPreview.create(recipe);
        appendInfo(recipe, counter, null, preview);
        boolean inline = inlineSlots(slotArea.width, counter.slots(), width);
        int stage = inline ? Math.max(slotArea.height, UISizes.SLOT) : slotArea.height;
        int left = panelHeight(preview != null && preview.hasStepper(), counter.labeled(), counter.sentenceRows(frame.besideNotch(width)));
        int footer = footerHeight(inline ? 0 : counter.slots(), frame.besideNotch(width));
        if (footer > 0) left += (left > 0 ? UISizes.SECTION_GAP : 0) + footer;
        int lower = Math.max(left, frame.notchHeight());
        int height = stage + (lower > 0 ? UISizes.SECTION_GAP + lower : 0);
        return new Size(width, Math.max(height, frame.fillHeight()));
    }

    private static boolean inlineSlots(int slotAreaWidth, int slots, int width) {
        return slots > 0 && slotAreaWidth + INLINE_SLOT_GAP + slots * UISizes.SLOT <= width - 2 * UITheme.PANEL_PADDING;
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
        var slotArea = recipe.recipeType.getRecipeUI().createRecipeTemplate(recipe, storages);
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
        return Math.max(1, (width - UISizes.GAP) / UISizes.SLOT);
    }

    private static int footerHeight(int slots, int width) {
        int perRow = slotsPerRow(width);
        return slots == 0 ? 0 : (slots + perRow - 1) / perRow * UISizes.SLOT;
    }

    @Nullable
    private Widget createFooter(RecipeInfoLines info, int width) {
        if (info.slots().isEmpty()) return null;
        var footer = new UIElement().layout(l -> l.row().gapAll(UISizes.GAP).alignItems(AlignItems.END));
        footer.addChild(RecipeSlotLayouts.grid(createSlots(info), slotsPerRow(frame.besideNotch(width))));
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
