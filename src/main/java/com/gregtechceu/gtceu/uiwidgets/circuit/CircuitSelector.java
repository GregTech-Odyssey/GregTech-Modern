package com.gregtechceu.gtceu.uiwidgets.circuit;

import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.recipe.content.Circuits;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.elements.SlotButton;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEItemKey;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

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
 */
public final class CircuitSelector {

    /// 每行几个编号（与槽位行对齐）
    private static final int PER_ROW = UISizes.SLOTS_PER_ROW;
    private static final String CLEAR = "gtceu.gui.circuit.clear";

    private CircuitSelector() {}

    public static UIElement create(NotifiableInventory<AEItemKey> circuitSlot) {
        return create(circuitSlot.storage);
    }

    public static UIElement create(KeyInventory<AEItemKey> circuitSlot) {
        var root = UIElement.column(UISizes.SLOT_ROW_WIDTH).layout(l -> l.gapAll(UISizes.SECTION_GAP));
        var clear = Button.glyph("×").setVariant(UITheme.ButtonVariant.DANGER)
                .setOnServerClick(() -> Circuits.set(circuitSlot, 0, -1));
        clear.tooltips(Component.translatable(CLEAR));
        root.addChild(UIElement.row(UISizes.SLOT_SIZE).layout(l -> l.gapAll(UISizes.SECTION_GAP).alignCenter())
                .addChildren(ItemSlot.display(circuitSlot, 0, null), clear));

        var grid = grid(() -> currentOf(circuitSlot), circuit -> setCircuit(circuitSlot, circuit));
        return root.addChild(grid);
    }

    public static UIElement grid(IntSupplier current, IntConsumer select) {
        var grid = UIElement.column(UISizes.SLOT_ROW_WIDTH);
        var synced = grid.addSyncValue(SyncValue.ofInt(current::getAsInt, -1));
        for (int rowStart = 0; rowStart <= Circuits.MAX; rowStart += PER_ROW) {
            var row = UIElement.row(UISizes.SLOT_SIZE);
            for (int n = rowStart; n <= Math.min(Circuits.MAX, rowStart + PER_ROW - 1); n++) {
                int circuit = n;
                var stack = IntCircuitBehaviour.stack(circuit);
                var cell = SlotButton.of(new ItemStackTexture(stack))
                        .setSelected(() -> synced.getValue() == circuit)
                        .setOnServerClick(() -> select.accept(circuit));
                cell.tooltips(stack.getHoverName());
                row.addChild(cell);
            }
            grid.addChild(row);
        }
        return grid;
    }

    private static int currentOf(KeyInventory<AEItemKey> circuitSlot) {
        return Circuits.get(circuitSlot, 0);
    }

    public static boolean isCurrent(KeyInventory<AEItemKey> circuitSlot, int circuit) {
        return circuit >= 0 && Circuits.get(circuitSlot, 0) == circuit;
    }

    /** 服务端：设为指定编号；已有编程电路时只改编号（保留物品上的其他数据）。 */
    public static void setCircuit(KeyInventory<AEItemKey> circuitSlot, int circuit) {
        var key = circuitSlot.keyAt(0);
        if (key != null && key.getItem() == Circuits.item() && key.getTag() != null && key.getTag().size() > 1) {
            var stack = key.toStack();
            IntCircuitBehaviour.setCircuitConfiguration(stack, circuit);
            circuitSlot.set(0, Keys.item(stack), circuitSlot.amountAt(0));
        } else {
            Circuits.set(circuitSlot, 0, circuit);
        }
    }
}
