package com.gregtechceu.gtceu.api.item.tool.aoe;

import com.gregtechceu.gtceu.api.item.ItemDataComponentKey;
import com.gregtechceu.gtceu.api.item.tool.ToolData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * 范围采掘的三向扩展格数；存在物品上的是玩家选择，缺失的分量按工具上限处理，读出后一律夹到上限内。
 */
public record AoESymmetrical(int column, int row, int layer) {

    public static final String COLUMN_KEY = "AoEColumn";
    public static final String ROW_KEY = "AoERow";
    public static final String LAYER_KEY = "AoELayer";
    private static final int UNSET = Integer.MAX_VALUE;

    public static final AoESymmetrical ZERO = new AoESymmetrical(0, 0, 0);

    public static final ItemDataComponentKey<AoESymmetrical, CompoundTag> KEY = ItemDataComponentKey.createCompound(ToolData.BEHAVIOURS_TAG_KEY, AoESymmetrical::write, AoESymmetrical::read, AoESymmetrical::remove);

    public AoESymmetrical {
        column = Math.max(0, column);
        row = Math.max(0, row);
        layer = Math.max(0, layer);
    }

    public static AoESymmetrical of(int column, int row, int layer) {
        return column <= 0 && row <= 0 && layer <= 0 ? ZERO : new AoESymmetrical(column, row, layer);
    }

    public boolean isZero() {
        return column == 0 && row == 0 && layer == 0;
    }

    public AoESymmetrical clampTo(AoESymmetrical max) {
        if (column <= max.column && row <= max.row && layer <= max.layer) return this;
        return of(Math.min(column, max.column), Math.min(row, max.row), Math.min(layer, max.layer));
    }

    public AoESymmetrical withColumn(int column) {
        return of(column, row, layer);
    }

    public AoESymmetrical withRow(int row) {
        return of(column, row, layer);
    }

    public AoESymmetrical withLayer(int layer) {
        return of(column, row, layer);
    }

    private static void write(CompoundTag tag, AoESymmetrical value) {
        tag.putInt(COLUMN_KEY, value.column);
        tag.putInt(ROW_KEY, value.row);
        tag.putInt(LAYER_KEY, value.layer);
    }

    private static void remove(CompoundTag tag) {
        tag.remove(COLUMN_KEY);
        tag.remove(ROW_KEY);
        tag.remove(LAYER_KEY);
    }

    private static AoESymmetrical read(CompoundTag tag) {
        boolean hasColumn = tag.contains(COLUMN_KEY, Tag.TAG_ANY_NUMERIC);
        boolean hasRow = tag.contains(ROW_KEY, Tag.TAG_ANY_NUMERIC);
        boolean hasLayer = tag.contains(LAYER_KEY, Tag.TAG_ANY_NUMERIC);
        if (!hasColumn && !hasRow && !hasLayer) return null;
        return new AoESymmetrical(hasColumn ? tag.getInt(COLUMN_KEY) : UNSET, hasRow ? tag.getInt(ROW_KEY) : UNSET,
                hasLayer ? tag.getInt(LAYER_KEY) : UNSET);
    }
}
