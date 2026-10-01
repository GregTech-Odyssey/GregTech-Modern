package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import java.util.List;
import java.util.function.IntFunction;

/**
 * 紧排的槽位网格：{@link #of} 按给定列数排，最后一行靠左；{@link #square} 排成近似正方形且尽量每行排满（8 格 4×2、18 格 6×3）。
 */
public final class SlotGrid {

    private SlotGrid() {}

    public static UIElement of(int columns, int count, IntFunction<? extends Widget> slot) {
        int perRow = Math.max(1, columns);
        var grid = UIElement.column(Math.min(perRow, count) * UISizes.SLOT_SIZE);
        for (int start = 0; start < count; start += perRow) {
            var row = UIElement.row(UISizes.SLOT_SIZE);
            for (int i = start; i < Math.min(count, start + perRow); i++) row.addChild(slot.apply(i));
            grid.addChild(row);
        }
        return grid;
    }

    public static UIElement of(int columns, List<? extends Widget> slots) {
        return of(columns, slots.size(), slots::get);
    }

    public static UIElement square(int count, IntFunction<? extends Widget> slot) {
        return of(squareColumns(count), count, slot);
    }

    public static int squareColumns(int count) {
        int square = Math.max(1, Math.min(UISizes.SLOTS_PER_ROW, (int) Math.ceil(Math.sqrt(count))));
        int widest = Math.min(UISizes.SLOTS_PER_ROW, (int) Math.floor(2 * Math.sqrt(count)));
        for (int columns = square; columns <= widest; columns++) {
            if (count % columns == 0) return columns;
        }
        return square;
    }

    public static int squareRows(int count) {
        int columns = squareColumns(count);
        return (count + columns - 1) / columns;
    }
}
