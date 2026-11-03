package com.gregtechceu.gtceu.api.recipe.ui;

import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.SlotGrid;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方槽位区的排布：默认排布 {@link #DEFAULT}，以及写专用排布用的小工具（网格、按底图坐标摆放的画布）。
 */
public final class RecipeSlotLayouts {

    private RecipeSlotLayouts() {}

    /** 固定每行格数的网格（如配方编辑器的虚拟槽）每行最多的槽数。 */
    public static final int SIDE_COLUMNS = 3;
    /** 槽位区四周的留白（与原版配方页一致）。 */
    public static final int PADDING = 4;
    /** 进度箭头与两侧槽位之间的距离。 */
    public static final int PROGRESS_MARGIN = 14;
    private static final int COMPACT_PADDING_VERTICAL = 2;
    private static final int COMPACT_PROGRESS_MARGIN = 8;
    private static final int TEMPLATE_COLUMNS = 3;
    private static final int TEMPLATE_ROWS = 3;
    public static final int DISPLAY_SLOT_GAP = 6;

    /**
     * 默认排布：{@code [输入] → [输出]}。
     * <ul>
     * <li>每一侧按内容种类依次排（物品在前、流体在后……），每种一个尽量方正的网格（{@link #columns}：4 格 2×2、5~6 格 3×2……），
     * 另起一行的网格与上面的网格同宽对齐；</li>
     * <li>第二种（通常是流体）放得下时竖排在第一种右边（第一种不足 3 列且行数够），否则另起一行；</li>
     * <li>两侧等宽（取较宽的一侧），各自居中，进度箭头因此总在正中；</li>
     * <li>一侧没有槽位时（如发电机界面不显示输出）照样占位，箭头不偏。</li>
     * </ul>
     */
    public static final RecipeSlotLayout DEFAULT = RecipeSlotLayout.fitting(slots -> {
        if (slots.isRecipeView()) return fixed(slots);
        var inputs = side(slots, IO.IN);
        var outputs = side(slots, IO.OUT);
        int sideWidth = Math.max(sideWidth(slots, IO.IN), sideWidth(slots, IO.OUT));
        inputs.layout(l -> l.width(sideWidth));
        outputs.layout(l -> l.width(sideWidth));
        var progress = slots.progress();
        return new UIElement()
                .layout(l -> l.row().paddingAll(PADDING).gapAll(PROGRESS_MARGIN).alignCenter())
                .addChildren(inputs, progress, outputs);
    });

    private static UIElement fixed(RecipeSlots slots) {
        var shapes = sideShapes(slots.templateCount(IO.IN, ItemRecipeInfo.INSTANCE), slots.templateCount(IO.IN, FluidRecipeInfo.INSTANCE),
                slots.templateCount(IO.OUT, ItemRecipeInfo.INSTANCE), slots.templateCount(IO.OUT, FluidRecipeInfo.INSTANCE), slots.reservedWidth());
        var row = new UIElement().layout(l -> l.row().paddingHorizontal(PADDING).paddingVertical(COMPACT_PADDING_VERTICAL)
                .gapAll(COMPACT_PROGRESS_MARGIN).alignCenter());
        var inputs = fixedSide(slots, IO.IN, shapes[0], AlignItems.END);
        if (inputs != null) row.addChild(inputs);
        row.addChild(slots.progress());
        var outputs = fixedSide(slots, IO.OUT, shapes[1], AlignItems.START);
        if (outputs != null) row.addChild(outputs);
        return row;
    }

    @Nullable
    private static UIElement fixedSide(RecipeSlots slots, IO io, SideShape shape, AlignItems align) {
        var caps = slots.capabilities(io);
        if (caps.isEmpty()) return null;
        var side = new UIElement().layout(l -> l.column().alignItems(align).minWidth(shape.width() * UISizes.SLOT_SIZE));
        UIElement besideRow = null;
        for (var cap : caps) {
            var capSlots = slots.slots(io, cap);
            if (slots.templateCount(io, cap) == 0) {
                side.addChild(SlotGrid.of(Math.max(1, Math.min(capSlots.size(), Math.max(shape.width(), 1))), capSlots));
            } else if (cap == ItemRecipeInfo.INSTANCE && shape.beside()) {
                besideRow = new UIElement().layout(l -> l.row().alignStart());
                besideRow.addChild(SlotGrid.of(shape.itemColumns(), capSlots));
                side.addChild(besideRow);
            } else if (cap == FluidRecipeInfo.INSTANCE && besideRow != null) {
                besideRow.addChild(SlotGrid.of(1, capSlots));
            } else {
                int columns = cap == ItemRecipeInfo.INSTANCE ? shape.itemColumns() : cap == FluidRecipeInfo.INSTANCE ? shape.fluidColumns() : Math.max(1, shape.width());
                side.addChild(SlotGrid.of(Math.max(1, columns), capSlots));
            }
        }
        return side;
    }

    private record SideShape(int itemColumns, int fluidColumns, boolean beside, int width, int rows) {}

    private static final SideShape NO_SIDE = new SideShape(0, 0, false, 0, 0);
    private static final int TEMPLATE_WIDTH = UISizes.CONTENT_WIDTH - 2 * UISizes.PANEL_PADDING - 2 * PADDING -
            RecipeSlots.PROGRESS_SIZE - 2 * COMPACT_PROGRESS_MARGIN;

    private static SideShape sideShape(int items, int fluids, boolean allowBeside) {
        if (items + fluids == 0) return NO_SIDE;
        if (items == 0) {
            int columns = Math.min(TEMPLATE_COLUMNS, fluids);
            return new SideShape(0, columns, false, columns, rows(fluids, columns));
        }
        int itemColumns = Math.min(TEMPLATE_COLUMNS, columns(items));
        int itemRows = rows(items, itemColumns);
        if (fluids == 0) return new SideShape(itemColumns, 0, false, itemColumns, itemRows);
        if (allowBeside && fluids <= itemRows) return new SideShape(itemColumns, 1, true, itemColumns + 1, itemRows);
        int columns = Math.max(itemColumns, Math.min(TEMPLATE_COLUMNS, fluids));
        return new SideShape(columns, columns, false, columns, rows(items, columns) + rows(fluids, columns));
    }

    private static SideShape[] sideShapes(int itemInputs, int fluidInputs, int itemOutputs, int fluidOutputs, int reserved) {
        int budget = TEMPLATE_WIDTH - reserved;
        var inputs = sideShape(itemInputs, fluidInputs, true);
        var outputs = sideShape(itemOutputs, fluidOutputs, true);
        if ((inputs.width() + outputs.width()) * UISizes.SLOT_SIZE > budget) {
            if (inputs.width() >= outputs.width()) inputs = sideShape(itemInputs, fluidInputs, false);
            else outputs = sideShape(itemOutputs, fluidOutputs, false);
        }
        if ((inputs.width() + outputs.width()) * UISizes.SLOT_SIZE > budget) {
            inputs = sideShape(itemInputs, fluidInputs, false);
            outputs = sideShape(itemOutputs, fluidOutputs, false);
        }
        return new SideShape[] { inputs, outputs };
    }

    private static int rows(int count, int columns) {
        return columns == 0 ? 0 : (count + columns - 1) / columns;
    }

    public static long trimTemplate(int itemInputs, int fluidInputs, int itemOutputs, int fluidOutputs, int reserved) {
        while (true) {
            var shapes = sideShapes(itemInputs, fluidInputs, itemOutputs, fluidOutputs, reserved);
            if (shapes[0].rows() > TEMPLATE_ROWS) {
                var trimmed = trimmed(itemInputs, fluidInputs, shapes[0]);
                if (trimmed == null) break;
                itemInputs = trimmed[0];
                fluidInputs = trimmed[1];
            } else if (shapes[1].rows() > TEMPLATE_ROWS) {
                var trimmed = trimmed(itemOutputs, fluidOutputs, shapes[1]);
                if (trimmed == null) break;
                itemOutputs = trimmed[0];
                fluidOutputs = trimmed[1];
            } else break;
        }
        var shapes = sideShapes(itemInputs, fluidInputs, itemOutputs, fluidOutputs, reserved);
        if (fluidInputs > 0) itemInputs = shapes[0].itemColumns() * rows(itemInputs, shapes[0].itemColumns());
        if (fluidOutputs > 0) itemOutputs = shapes[1].itemColumns() * rows(itemOutputs, shapes[1].itemColumns());
        return GTRecipeTypeUI.pack(itemInputs, fluidInputs, itemOutputs, fluidOutputs);
    }

    @Nullable
    private static int[] trimmed(int items, int fluids, SideShape shape) {
        int itemRows = rows(items, shape.itemColumns());
        int fluidRows = shape.beside() ? 0 : rows(fluids, shape.fluidColumns());
        if (itemRows > 1) return new int[] { (itemRows - 1) * shape.itemColumns(), Math.min(fluids, shape.beside() ? itemRows - 1 : fluids) };
        if (fluidRows > 1) return new int[] { items, (fluidRows - 1) * shape.fluidColumns() };
        return null;
    }

    /**
     * 一侧：各种类的网格上下排列，左边缘对齐，整块在这一侧里水平居中；第二种放得下时竖排在第一种右边。
     * 另起一行的网格至少与上面最宽的网格同列数（格数不够时排成一行），上下网格的边缘因此对齐（如 16 格物品下面 4 格流体排成一行）。
     */
    private static UIElement side(RecipeSlots slots, IO io) {
        var groups = new ArrayList<List<Widget>>();
        for (var cap : slots.capabilities(io)) {
            var capSlots = slots.slots(io, cap);
            if (!capSlots.isEmpty()) groups.add(capSlots);
        }
        var stack = new UIElement().layout(l -> l.column().alignStart());
        int next = 0, widest = 0;
        if (groups.size() >= 2 && beside(groups.get(0).size(), groups.get(1).size())) {
            var first = groups.get(0);
            widest = columns(first.size()) + 1;
            stack.addChild(new UIElement().layout(l -> l.row().alignStart())
                    .addChildren(SlotGrid.of(columns(first.size()), first), SlotGrid.of(1, groups.get(1))));
            next = 2;
        }
        for (int i = next; i < groups.size(); i++) {
            var group = groups.get(i);
            int columns = stackedColumns(group.size(), widest);
            widest = Math.max(widest, columns);
            stack.addChild(SlotGrid.of(columns, group));
        }
        return new UIElement().layout(l -> l.column().alignCenter()).addChild(stack);
    }

    /** 另起一行的网格的列数：自身方正的列数与上面最宽的网格取大，但不超过格数。 */
    private static int stackedColumns(int count, int widest) {
        return Math.min(count, Math.max(columns(count), widest));
    }

    private static int sideWidth(RecipeSlots slots, IO io) {
        var counts = new IntArrayList();
        for (var cap : slots.capabilities(io)) {
            int count = slots.count(io, cap);
            if (count > 0) counts.add(count);
        }
        int columns = 0, next = 0;
        if (counts.size() >= 2 && beside(counts.getInt(0), counts.getInt(1))) {
            columns = columns(counts.getInt(0)) + 1;
            next = 2;
        }
        for (int i = next; i < counts.size(); i++) columns = Math.max(columns, stackedColumns(counts.getInt(i), columns));
        return columns * UISizes.SLOT_SIZE;
    }

    /**
     * {@code count} 格排成尽量方正的网格时的列数（与原 GTM 配方页一致）：完全平方数排成正方形，3 格一行，
     * 其余取 ⌈√count⌉ 列。
     */
    public static int columns(int count) {
        if (count <= 3) return Math.max(count, 1);
        return (int) Math.ceil(Math.sqrt(count));
    }

    /** 第二种内容（{@code secondCount} 格）能否竖排在第一种（{@code firstCount} 格）右边：第一种不足 3 列，且行数不少于第二种的格数。 */
    private static boolean beside(int firstCount, int secondCount) {
        int columns = columns(firstCount);
        int rows = (firstCount + columns - 1) / columns;
        return columns < 3 && rows >= secondCount;
    }

    /**
     * 按底图坐标摆放的画布，固定 {@code width × height}。带整张底图的专用排布（装配线、蒸馏塔、研究站……）里，
     * 槽位和进度条要对准底图上的位置，用它按像素坐标摆放；其余排布用 flex。
     */
    public static UIElement canvas(int width, int height) {
        return new UIElement().layout(l -> l.size(width, height));
    }

    /** 在画布 {@code canvas} 的 {@code (x, y)} 处放一个控件。 */
    public static void place(UIElement canvas, Widget widget, int x, int y) {
        var holder = new UIElement().layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(x).top(y));
        holder.addChild(widget);
        canvas.addChild(holder);
    }

    /** 在画布上放一张装饰图（锤子底座、车床夹头……）。 */
    public static void image(UIElement canvas, IGuiTexture texture, int x, int y, int width, int height) {
        place(canvas, new ImageWidget(0, 0, width, height, texture), x, y);
    }
}
