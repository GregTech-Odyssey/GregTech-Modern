package com.gregtechceu.gtceu.uiwidgets.recipe;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.render.UIText;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 配方页内凹参数网格：首行固定「耗能 | 等级」，其余按标准两列连续排（耗时、总计、条件、属性依次补空位，末尾单出的半格不合并，只有一项时占满整行，说明各占一行），百万以上的数值缩写；双色棋盘格填充。
 * 版位由 {@link #plan} 一次算出，配方页量尺寸与建控件共用；超出行数上限时说明、属性、总计依次让位，计入悬停的"+N 项"。
 */
public final class RecipeSpecGrid extends UIElement {

    public static final int ROW_HEIGHT = UISizes.TEXT_HEIGHT + 3;
    private static final int FRAME = 1;
    private static final int CELL_PADDING = 2;
    private static final int LABEL_VALUE_GAP = UISizes.TEXT_PADDING + 2;
    public static final int TOP = 3;
    private static final int BOTTOM = 1;
    private static final Component TIER_LABEL = Component.translatable("gtceu.tip.stat.tier");
    private static final double ABBREVIATE_FROM = 1_000_000;

    public enum Kind {
        STAT,
        REQUIREMENT,
        PROPERTY,
        SENTENCE,
        LINK
    }

    public record Cell(Kind kind, @Nullable Component label, Supplier<Component> value, @Nullable Runnable onClick) {

        public static Cell stat(Component label, Supplier<Component> value) {
            return new Cell(Kind.STAT, label, value, null);
        }

        public static Cell of(RecipeInfoLines.Line line) {
            var kind = switch (line.kind()) {
                case REQUIREMENT -> Kind.REQUIREMENT;
                case PROPERTY -> Kind.PROPERTY;
                case SENTENCE -> Kind.SENTENCE;
                case LINK -> Kind.LINK;
            };
            return new Cell(kind, line.label(), line.value(), line.onClick());
        }

        private boolean wide() {
            return kind == Kind.SENTENCE || kind == Kind.LINK;
        }
    }

    public record Placed(Cell cell, int row, int column) {}

    public record Plan(List<Placed> cells, int rows, int tierRow, List<Cell> hidden) {

        public int height() {
            return rows == 0 ? 0 : TOP + rows * ROW_HEIGHT + BOTTOM;
        }
    }

    public static int heightOf(int rows) {
        return rows == 0 ? 0 : TOP + rows * ROW_HEIGHT + BOTTOM;
    }

    public static int rowsWithin(int height) {
        return Math.max(0, (height - TOP - BOTTOM) / ROW_HEIGHT);
    }

    public static int columnWidth(int width) {
        return (width - 2 * FRAME) / 2 - 2 * CELL_PADDING;
    }

    @OnlyIn(Dist.CLIENT)
    public static int valueWidth(Component value) {
        return UIText.width(abbreviated(value.getString(), Integer.MAX_VALUE));
    }

    @OnlyIn(Dist.CLIENT)
    public static int requiredWidth(Plan plan, int tierWidth) {
        int half = tierWidth > 0 ? UIText.width(TIER_LABEL) + LABEL_VALUE_GAP + tierWidth : 0;
        int full = 0, count = 0;
        for (var placed : plan.cells()) {
            var cell = placed.cell();
            int content = valueWidth(RecipeInfoLines.nonNull(cell.value().get()));
            if (cell.label() != null) content += UIText.width(cell.label().getString()) + LABEL_VALUE_GAP;
            if (cell.wide()) full = Math.max(full, content);
            else {
                half = Math.max(half, content);
                count++;
            }
        }
        int columns = plan.tierRow() >= 0 || count > 1 ? 2 : 1;
        return 2 * FRAME + Math.max(columns * (half + 2 * CELL_PADDING), full + 2 * CELL_PADDING);
    }

    @OnlyIn(Dist.CLIENT)
    public static Plan plan(@Nullable Cell rate, @Nullable Cell duration, @Nullable Cell total, boolean tier,
                            List<Cell> extras, int width, int maxRows) {
        var requirements = new ArrayList<Cell>();
        var properties = new ArrayList<Cell>();
        var sentences = new ArrayList<Cell>();
        for (var cell : extras) {
            switch (cell.kind()) {
                case REQUIREMENT -> requirements.add(cell);
                case PROPERTY, STAT -> properties.add(cell);
                default -> sentences.add(cell);
            }
        }
        var hidden = new ArrayList<Cell>();
        var shownTotal = total;
        while (true) {
            var plan = layout(rate, duration, shownTotal, tier, requirements, properties, sentences, width, hidden);
            if (plan.rows <= maxRows) return plan;
            int note = lastNote(sentences);
            if (note >= 0) hidden.add(0, sentences.remove(note));
            else if (!properties.isEmpty()) hidden.add(0, properties.remove(properties.size() - 1));
            else if (shownTotal != null) {
                hidden.add(0, shownTotal);
                shownTotal = null;
            } else if (!sentences.isEmpty()) hidden.add(0, sentences.remove(sentences.size() - 1));
            else return plan;
        }
    }

    private static int lastNote(List<Cell> sentences) {
        for (int i = sentences.size() - 1; i >= 0; i--) {
            if (sentences.get(i).kind() != Kind.LINK) return i;
        }
        return -1;
    }

    @OnlyIn(Dist.CLIENT)
    private static Plan layout(@Nullable Cell rate, @Nullable Cell duration, @Nullable Cell total, boolean tier,
                               List<Cell> requirements, List<Cell> properties, List<Cell> sentences, int width,
                               List<Cell> hidden) {
        int column = columnWidth(width);
        var cells = new ArrayList<Placed>();
        var stats = new ArrayList<Cell>(3);
        if (rate != null) stats.add(rate);
        if (duration != null) stats.add(duration);
        if (total != null) stats.add(total);
        int row = 0;
        int tierRow = -1;
        if (tier) {
            if (!stats.isEmpty()) cells.add(new Placed(stats.remove(0), row, 0));
            tierRow = row++;
        }
        var flowing = new ArrayList<Cell>(stats.size() + requirements.size() + properties.size());
        flowing.addAll(stats);
        flowing.addAll(requirements);
        flowing.addAll(properties);
        row = flow(flowing, cells, row, column, hidden.isEmpty() && row == 0 && flowing.size() == 1);
        for (var cell : sentences) cells.add(new Placed(cell, row++, -1));
        return new Plan(cells, row, tierRow, List.copyOf(hidden));
    }

    @OnlyIn(Dist.CLIENT)
    private static int flow(List<Cell> group, List<Placed> cells, int row, int column, boolean spanLone) {
        int open = -1;
        for (var cell : group) {
            if (!fitsHalf(cell, column)) {
                cells.add(new Placed(cell, row++, -1));
            } else if (open >= 0) {
                cells.add(new Placed(cell, open, 1));
                open = -1;
            } else {
                open = row;
                cells.add(new Placed(cell, row++, 0));
            }
        }
        if (open >= 0 && spanLone) {
            for (int i = cells.size() - 1; i >= 0; i--) {
                var placed = cells.get(i);
                if (placed.row() != open) continue;
                cells.set(i, new Placed(placed.cell(), open, -1));
                break;
            }
        }
        return row;
    }

    @OnlyIn(Dist.CLIENT)
    private static boolean fitsHalf(Cell cell, int column) {
        if (cell.wide()) return false;
        var font = Minecraft.getInstance().font;
        int label = cell.label() == null ? 0 : font.width(cell.label()) + LABEL_VALUE_GAP;
        String value = abbreviated(RecipeInfoLines.nonNull(cell.value().get()).getString(), column - label);
        return label + font.width(value) <= column;
    }

    private final Plan plan;
    private final int width;
    private final Entry[] entries;
    private final boolean[] splitRows;
    private final String more;
    private int moreWidth = -1;
    @Nullable
    private final Widget tier;

    public RecipeSpecGrid(Plan plan, int width, @Nullable Widget tier) {
        this.plan = plan;
        this.width = width;
        this.more = plan.hidden().isEmpty() ? "" : Component.translatable("gtceu.recipe.info.more", plan.hidden().size()).getString();
        this.tier = plan.tierRow() >= 0 ? tier : null;
        entries = new Entry[plan.cells().size()];
        for (int i = 0; i < entries.length; i++) entries[i] = new Entry(plan.cells().get(i));
        splitRows = new boolean[plan.rows()];
        for (var placed : plan.cells()) {
            if (placed.column() >= 0) splitRows[placed.row()] = true;
        }
        if (plan.tierRow() >= 0) splitRows[plan.tierRow()] = true;
        layout(l -> l.size(width, plan.height()));
        if (tier != null && plan.tierRow() >= 0) {
            var holder = new UIElement().layout(l -> l.positionType(TaffyPosition.ABSOLUTE).right(FRAME + CELL_PADDING).top(TOP + plan.tierRow() * ROW_HEIGHT));
            holder.addChild(tier);
            addChild(holder);
        }
    }

    private int markerRow() {
        return plan.tierRow() >= 0 ? plan.tierRow() : plan.rows() - 1;
    }

    private int moreX(int x0) {
        int right = tier == null ? x0 + width - FRAME - CELL_PADDING : tier.getPositionX() - UISizes.TEXT_PADDING;
        return Math.max(x0 + FRAME + (width - 2 * FRAME) / 2 + CELL_PADDING, right - moreWidth);
    }

    private int cellX(Placed placed) {
        return FRAME + (placed.column() == 1 ? (width - 2 * FRAME) / 2 : 0) + CELL_PADDING;
    }

    private int cellWidth(Placed placed) {
        return placed.column() < 0 ? width - 2 * (FRAME + CELL_PADDING) : columnWidth(width);
    }

    private int moreWidth() {
        if (moreWidth < 0) moreWidth = UIText.width(more);
        return moreWidth;
    }

    @OnlyIn(Dist.CLIENT)
    private void drawCells(GuiGraphics graphics, int x0, int y0) {
        int left = x0 + FRAME, right = x0 + width - FRAME;
        int middle = left + (width - 2 * FRAME) / 2;
        for (int row = 0; row < plan.rows(); row++) {
            int y = y0 + TOP + row * ROW_HEIGHT;
            int even = row % 2 == 0 ? UITheme.SPEC_CELL_LIGHT : UITheme.SPEC_CELL_DARK;
            int odd = row % 2 == 0 ? UITheme.SPEC_CELL_DARK : UITheme.SPEC_CELL_LIGHT;
            if (!splitRows[row]) {
                graphics.fill(left, y, right, y + ROW_HEIGHT, even);
                continue;
            }
            graphics.fill(left, y, middle, y + ROW_HEIGHT, even);
            graphics.fill(middle, y, right, y + ROW_HEIGHT, odd);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x0 = getPositionX(), y0 = getPositionY();
        if (plan.rows() > 0) {
            UITheme.STATUS_PANEL.draw(graphics, mouseX, mouseY, x0, y0 + TOP - FRAME, width, plan.rows() * ROW_HEIGHT + 2 * FRAME);
            drawCells(graphics, x0, y0);
        }
        for (var entry : entries) entry.draw(graphics, x0, y0, mouseX, mouseY);
        if (tier != null) {
            int x = x0 + FRAME + (width - 2 * FRAME) / 2 + CELL_PADDING;
            int y = UIText.centerY(y0 + TOP + plan.tierRow() * ROW_HEIGHT, ROW_HEIGHT);
            UIText.drawLeft(graphics, TIER_LABEL, x, y, UITheme.TEXT_SECONDARY);
        }
        if (!plan.hidden().isEmpty() && plan.rows() > 0) {
            moreWidth();
            int y = UIText.centerY(y0 + TOP + markerRow() * ROW_HEIGHT, ROW_HEIGHT);
            UIText.drawLeft(graphics, more, moreX(x0), y, UITheme.PLACEHOLDER_TEXT);
        }
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
        int x0 = getPositionX(), y0 = getPositionY();
        for (var entry : entries) {
            if (!entry.isOver(x0, y0, mouseX, mouseY)) continue;
            var tooltip = entry.tooltip();
            if (tooltip != null) gui.getModularUIGui().setHoverTooltip(tooltip, ItemStack.EMPTY, null, null);
            return;
        }
        if (plan.hidden().isEmpty() || plan.rows() == 0) return;
        int y = y0 + TOP + markerRow() * ROW_HEIGHT;
        int x = moreX(x0);
        if (mouseY < y || mouseY >= y + ROW_HEIGHT || mouseX < x || mouseX >= x + moreWidth) return;
        var lines = new ArrayList<Component>(plan.hidden().size());
        for (var cell : plan.hidden()) lines.add(fullText(cell, RecipeInfoLines.nonNull(cell.value().get())));
        gui.getModularUIGui().setHoverTooltip(lines, ItemStack.EMPTY, null, null);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseOverElement(mouseX, mouseY)) {
            int x0 = getPositionX(), y0 = getPositionY();
            for (var entry : entries) {
                var onClick = entry.placed.cell().onClick();
                if (onClick == null || !entry.isOver(x0, y0, (int) mouseX, (int) mouseY)) continue;
                playButtonClickSound();
                onClick.run();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static Component fullText(Cell cell, Component value) {
        return cell.label() == null ? value : cell.label().copy().append("  ").append(value);
    }

    private final class Entry {

        private final Placed placed;
        @Nullable
        private Component source;
        private String label = "";
        private String shown = "";
        private int labelWidth;
        private boolean truncated;

        private Entry(Placed placed) {
            this.placed = placed;
        }

        @OnlyIn(Dist.CLIENT)
        private void refresh() {
            var value = RecipeInfoLines.nonNull(placed.cell().value().get());
            if (value == source) return;
            source = value;
            var cell = placed.cell();
            label = cell.label() == null ? "" : cell.label().getString();
            labelWidth = label.isEmpty() ? 0 : UIText.width(label) + LABEL_VALUE_GAP;
            int available = cellWidth(placed) - labelWidth;
            String text = value.getString();
            shown = cell.wide() ? UIText.fit(text, available) : compact(text, available);
            truncated = !shown.equals(text);
        }

        @OnlyIn(Dist.CLIENT)
        private void draw(GuiGraphics graphics, int x0, int y0, int mouseX, int mouseY) {
            refresh();
            var cell = placed.cell();
            int x = x0 + cellX(placed), y = y0 + TOP + placed.row() * ROW_HEIGHT, w = cellWidth(placed);
            int textY = UIText.centerY(y, ROW_HEIGHT);
            if (cell.label() != null) {
                int labelColor = cell.kind() == Kind.REQUIREMENT ? UITheme.TEXT : UITheme.TEXT_SECONDARY;
                UIText.drawLeft(graphics, label, x, textY, labelColor);
                UIText.drawRight(graphics, shown, x + w, textY, UITheme.TEXT);
                return;
            }
            if (cell.kind() == Kind.LINK) {
                UIText.drawLeft(graphics, shown, x, textY, UITheme.LINK_TEXT);
                if (isOver(x0, y0, mouseX, mouseY)) graphics.fill(x, textY + 8, x + UIText.width(shown), textY + 9, UITheme.LINK_TEXT);
                return;
            }
            UIText.drawLeft(graphics, shown, x, textY, UITheme.TEXT_SECONDARY);
        }

        private boolean isOver(int x0, int y0, int mouseX, int mouseY) {
            int x = x0 + cellX(placed), y = y0 + TOP + placed.row() * ROW_HEIGHT;
            return mouseX >= x && mouseX < x + cellWidth(placed) && mouseY >= y && mouseY < y + ROW_HEIGHT;
        }

        @Nullable
        private List<Component> tooltip() {
            if (source == null) return null;
            var hover = RecipeInfoLines.hoverLines(source);
            if (!truncated) return hover.isEmpty() ? null : hover;
            var lines = new ArrayList<Component>(1 + hover.size());
            lines.add(fullText(placed.cell(), source));
            lines.addAll(hover);
            return lines;
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static String compact(String text, int maxWidth) {
        String result = abbreviated(text, maxWidth);
        return UIText.width(result) <= maxWidth ? result : UIText.fit(result, maxWidth);
    }

    @OnlyIn(Dist.CLIENT)
    private static String abbreviated(String text, int maxWidth) {
        int end = 0;
        boolean decimal = false;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (Character.isDigit(c) || c == ',') end++;
            else if (c == '.' && !decimal && end > 0) {
                decimal = true;
                end++;
            } else break;
        }
        if (end == 0 || end < text.length() && (text.charAt(end) == '.' || text.charAt(end) == ',')) return text;
        double value;
        try {
            value = Double.parseDouble(text.substring(0, end).replace(",", ""));
        } catch (NumberFormatException e) {
            return text;
        }
        if (value < ABBREVIATE_FROM && UIText.width(text) <= maxWidth) return text;
        return new StringBuilder().append(FormattingUtil.formatNumberCompact(value)).append(text, end, text.length()).toString();
    }
}
