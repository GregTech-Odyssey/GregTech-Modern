package com.gregtechceu.gtceu.uiwidgets.circuit;

import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.elements.SlotButton;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 编程电路选择器：机器左侧"电路设置"展开后的内容。
 * 
 * <pre>
 *  [当前电路] [×]
 *  [0][1][2][3][4][5][6][7][8]     ← 点选格，当前编号带选中框
 *  [9] … [17]
 *  [18] … [26]
 *  [27] … [32]
 * </pre>
 * 
 * 宽 {@link UISizes#SLOT_ROW_WIDTH}，与物品槽网格同宽、同格距；当前电路格只作展示（统一斜纹）。
 * 点击都在服务端改电路格，两端的格子内容经槽位同步；选中框按本端电路格判定。
 */
public final class CircuitSelector {

    /// 每行几个编号（与槽位行对齐）
    private static final int PER_ROW = UISizes.SLOTS_PER_ROW;
    private static final String CLEAR = "gtceu.gui.circuit.clear";

    private CircuitSelector() {}

    public static UIElement create(CustomItemStackHandler circuitSlot) {
        var root = UIElement.column(UISizes.SLOT_ROW_WIDTH).layout(l -> l.gapAll(UISizes.SECTION_GAP));
        var clear = Button.glyph("×").setVariant(UITheme.ButtonVariant.DANGER)
                .setOnServerClick(() -> circuitSlot.setStackInSlot(0, ItemStack.EMPTY));
        clear.setHoverTooltips(Component.translatable(CLEAR));
        root.addChild(UIElement.row(UISizes.SLOT).layout(l -> l.gapAll(UISizes.SECTION_GAP).alignCenter())
                .addChildren(ItemSlot.display(circuitSlot, 0, null), clear));

        var grid = UIElement.column(UISizes.SLOT_ROW_WIDTH);
        for (int rowStart = 0; rowStart <= IntCircuitBehaviour.CIRCUIT_MAX; rowStart += PER_ROW) {
            var row = UIElement.row(UISizes.SLOT);
            for (int n = rowStart; n <= Math.min(IntCircuitBehaviour.CIRCUIT_MAX, rowStart + PER_ROW - 1); n++) {
                int circuit = n;
                var stack = IntCircuitBehaviour.stack(circuit);
                var cell = SlotButton.of(new ItemStackTexture(stack))
                        .setSelected(() -> isCurrent(circuitSlot, circuit))
                        .setOnServerClick(() -> setCircuit(circuitSlot, circuit));
                cell.setHoverTooltips(stack.getHoverName());
                row.addChild(cell);
            }
            grid.addChild(row);
        }
        return root.addChild(grid);
    }

    public static boolean isCurrent(CustomItemStackHandler circuitSlot, int circuit) {
        var stack = circuitSlot.getStackInSlot(0);
        return IntCircuitBehaviour.isIntegratedCircuit(stack) && IntCircuitBehaviour.getCircuitConfiguration(stack) == circuit;
    }

    /** 服务端：设为指定编号；已有编程电路时只改编号（保留物品上的其他数据）。 */
    public static void setCircuit(CustomItemStackHandler circuitSlot, int circuit) {
        ItemStack stack = circuitSlot.getStackInSlot(0).copy();
        if (IntCircuitBehaviour.isIntegratedCircuit(stack)) {
            IntCircuitBehaviour.setCircuitConfiguration(stack, circuit);
            circuitSlot.setStackInSlot(0, stack);
        } else {
            circuitSlot.setStackInSlot(0, IntCircuitBehaviour.stack(circuit));
        }
    }
}
