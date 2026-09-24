package com.gregtechceu.gtceu.uipro.styletemplate;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

import net.minecraft.resources.ResourceLocation;

/**
 * 机器小组件（窗口左侧的配置标签）图标图集。
 * <p>
 * 图标标准：每格 16×16，形体统一落在中间 12×12（四周各留 2 像素）；不向外描边，只在形体外缘画一圈同色系深色细线、
 * 内部平涂，不画高光暗面，主线条 1 像素。开关类图标"关"用灰色、"开"用彩色，形状不变。
 * <p>
 * 图集排布：每行一个图标，每列一个状态，宽固定 {@value #COLUMNS} 格。
 */
public final class WidgetIconAtlas {

    public static final int CELL = 16;
    public static final int COLUMNS = 4;

    private final ResourceTexture texture;
    private final int rows;

    public WidgetIconAtlas(ResourceLocation location, int rows) {
        this.texture = new ResourceTexture(location);
        this.rows = rows;
    }

    /** 第 {@code row} 行、第 {@code state} 个状态的图标。 */
    public IGuiTexture icon(int row, int state) {
        return texture.getSubTexture((double) state / COLUMNS, (double) row / rows, 1.0 / COLUMNS, 1.0 / rows);
    }

    /** 只有一个状态的图标。 */
    public IGuiTexture icon(int row) {
        return icon(row, 0);
    }
}
