package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.ILayoutItem;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntFunction;
import java.util.function.IntSupplier;

public class VirtualList extends WidgetGroup implements ILayoutItem {

    private static final int OVERSCAN_ROWS = 1;

    private final LayoutStyle layoutStyle;
    private final int columns;
    private final int rowHeight;
    private final int gap;
    private final IntSupplier count;
    private final IntFunction<Widget> factory;
    private final Int2ObjectOpenHashMap<Widget> live = new Int2ObjectOpenHashMap<>();
    private int shownCount = -1;
    private int cellWidth = -1;

    public VirtualList(int columns, int rowHeight, int gap, IntSupplier count, IntFunction<Widget> factory) {
        super(0, 0, 0, 0);
        this.columns = Math.max(1, columns);
        this.rowHeight = rowHeight;
        this.gap = gap;
        this.count = count;
        this.factory = factory;
        this.layoutStyle = LayoutStyle.fixed(LayoutStyle.AUTO, 0, () -> UIElement.markLayoutDirty(this));
        setClientSideWidget();
        updateHeight();
    }

    @Override
    public LayoutStyle getLayoutStyle() {
        return layoutStyle;
    }

    public void refresh() {
        for (var widget : live.values()) removeWidget(widget);
        live.clear();
        updateHeight();
    }

    private void updateHeight() {
        int items = Math.max(0, count.getAsInt());
        if (items == shownCount) return;
        shownCount = items;
        int rows = (items + columns - 1) / columns;
        layoutStyle.height(rows == 0 ? 0 : rows * rowHeight + (rows - 1) * gap);
    }

    @Override
    public void setSize(Size size) {
        super.setSize(size);
        int width = columns == 1 ? size.width : (size.width - (columns - 1) * gap) / columns;
        if (width != cellWidth) {
            cellWidth = width;
            for (var widget : live.values()) removeWidget(widget);
            live.clear();
        }
    }

    @Override
    protected void onChildSizeUpdate(Widget child) {}

    private void updateVisible() {
        updateHeight();
        if (cellWidth <= 0 || shownCount == 0) return;
        int top = 0, bottom = getSizeHeight();
        for (var ancestor = getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof ScrollerView scroller) {
                top = Math.max(top, scroller.getPositionY() - getPositionY());
                bottom = Math.min(bottom, scroller.getPositionY() + scroller.getSizeHeight() - getPositionY());
                break;
            }
        }
        int pitch = rowHeight + gap;
        int firstRow = Math.max(0, top / pitch - OVERSCAN_ROWS);
        int lastRow = Math.min((shownCount - 1) / columns, Math.max(0, bottom) / pitch + OVERSCAN_ROWS);
        int first = firstRow * columns, last = Math.min(shownCount - 1, lastRow * columns + columns - 1);
        if (bottom <= top) {
            first = 0;
            last = -1;
        }
        for (var it = live.int2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            int index = entry.getIntKey();
            if (index < first || index > last) {
                removeWidget(entry.getValue());
                it.remove();
            }
        }
        for (int index = first; index <= last; index++) {
            if (live.containsKey(index)) continue;
            var widget = factory.apply(index);
            int row = index / columns, column = index % columns;
            if (widget instanceof UIElement element) element.layout(l -> l.width(cellWidth));
            else widget.setSize(new Size(cellWidth, rowHeight));
            widget.setSelfPosition(new Position(column * (cellWidth + gap), row * (rowHeight + gap)));
            live.put(index, widget);
            addWidget(widget);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateScreen() {
        updateVisible();
        super.updateScreen();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        updateVisible();
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }
}
