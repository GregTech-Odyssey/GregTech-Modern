package com.gregtechceu.gtceu.uipro.flow;

import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.canvas.CanvasPainter;
import com.gregtechceu.gtceu.uipro.canvas.CanvasRect;
import com.gregtechceu.gtceu.uipro.canvas.CanvasRoute;
import com.gregtechceu.gtceu.uipro.canvas.CanvasWire;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.view.FlowView;
import com.gregtechceu.gtceu.uipro.view.ZoomBar;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

public class FlowChart extends UIElement {

    private static final FlowState[] STATES = FlowState.values();

    private final int[] columnWidths;
    private final int[] columnStarts;
    private final int gutter;
    private final List<UIElement> rows = new ArrayList<>();
    private final List<FlowNode> nodes = new ArrayList<>();
    private final List<FlowLink> links = new ArrayList<>();
    private final List<Wire> wires = new ArrayList<>();
    private final List<Junction> junctions = new ArrayList<>();
    private int[] geometry = new int[0];
    private boolean wiresValid;
    @Nullable
    private CanvasPainter painter;

    public FlowChart(int width, int columns) {
        this(width, columns, 0);
    }

    public FlowChart(int width, int columns, int leftGutter) {
        this(leftGutter, uniformColumns(width, columns, leftGutter));
    }

    public FlowChart(int leftGutter, int... columnWidths) {
        this.gutter = Math.max(0, leftGutter);
        this.columnWidths = columnWidths.length == 0 ? new int[] { UISizes.CONTENT_WIDTH } : columnWidths.clone();
        this.columnStarts = new int[this.columnWidths.length];
        int x = 0;
        for (int i = 0; i < this.columnWidths.length; i++) {
            columnStarts[i] = x;
            x += this.columnWidths[i] + UISizes.FLOW_COLUMN_GAP;
        }
        int inner = x - UISizes.FLOW_COLUMN_GAP;
        layout(l -> l.column().width(inner + 2 * UISizes.FLOW_PADDING + gutter).rowGap(UISizes.FLOW_CHANNEL_WIDTH)
                .paddingAll(UISizes.FLOW_PADDING).paddingLeft(UISizes.FLOW_PADDING + gutter));
    }

    private static int[] uniformColumns(int width, int columns, int leftGutter) {
        int count = Math.max(1, columns);
        int inner = width - 2 * UISizes.FLOW_PADDING - Math.max(0, leftGutter);
        int base = Math.max(1, (inner - (count - 1) * UISizes.FLOW_COLUMN_GAP) / count);
        int extra = Math.max(0, inner - count * base - (count - 1) * UISizes.FLOW_COLUMN_GAP);
        int[] widths = new int[count];
        for (int i = 0; i < count; i++) widths[i] = base + (i < extra ? 1 : 0);
        return widths;
    }

    public int getColumnCount() {
        return columnWidths.length;
    }

    public int getColumnWidth(int column) {
        return columnWidths[Math.max(0, Math.min(column, columnWidths.length - 1))];
    }

    public int widthFor(int span) {
        return widthFor(0, span);
    }

    public int widthFor(int column, int span) {
        int first = Math.max(0, Math.min(column, columnWidths.length - 1));
        int last = Math.max(first, Math.min(column + span - 1, columnWidths.length - 1));
        return columnStarts[last] + columnWidths[last] - columnStarts[first];
    }

    public FlowView toView(boolean inventory) {
        return new FlowView("flow", this, false).limitToWindow(inventory);
    }

    public FlowView toView(FancyMachineUIWidget window, boolean inventory, String zoomKey) {
        var view = new FlowView(zoomKey, this, true).limitToWindow(inventory);
        if (window instanceof MachineWindow machineWindow) machineWindow.addTitleTool(() -> ZoomBar.title(view));
        return view;
    }

    public record Tree(int[] row, int[] column, int[] span, int columns) {}

    public static Tree tree(int count, int root, IntFunction<int[]> children) {
        int[] span = new int[count], column = new int[count], row = new int[count];
        measure(root, children, span);
        place(root, 0, 0, children, span, column, row);
        return new Tree(row, column, span, span[root]);
    }

    private static int measure(int index, IntFunction<int[]> children, int[] span) {
        int sum = 0;
        for (int child : children.apply(index)) sum += measure(child, children, span);
        span[index] = Math.max(1, sum);
        return span[index];
    }

    private static void place(int index, int depth, int start, IntFunction<int[]> children, int[] span, int[] column, int[] row) {
        row[index] = depth;
        column[index] = start;
        int next = start;
        for (int child : children.apply(index)) {
            place(child, depth + 1, next, children, span, column, row);
            next += span[child];
        }
    }

    public FlowNode node(int row, int column) {
        return node(row, column, 1);
    }

    public FlowNode node(int row, int column, int span) {
        while (rows.size() <= row) {
            var line = new UIElement().layout(l -> l.row());
            rows.add(line);
            addChild(line);
        }
        var node = new FlowNode(row, column, Math.max(1, span), widthFor(column, Math.max(1, span)));
        var line = rows.get(row);
        int index = 0;
        while (index < line.widgets.size() && line.widgets.get(index) instanceof FlowNode other && other.column < column) index++;
        line.addWidget(index, node);
        nodes.add(node);
        int previousEnd = 0;
        for (var widget : line.widgets) {
            if (!(widget instanceof FlowNode placed)) continue;
            int start = columnStarts[Math.max(0, Math.min(placed.column, columnStarts.length - 1))];
            int margin = start - previousEnd;
            placed.layout(l -> l.marginLeft(margin));
            previousEnd = start + widthFor(placed.column, placed.span);
        }
        wiresValid = false;
        return node;
    }

    public FlowLink link(FlowNode from, FlowNode to) {
        var link = new FlowLink(this, from, to);
        links.add(link);
        wiresValid = false;
        return link;
    }

    public List<FlowNode> getNodes() {
        return nodes;
    }

    public List<FlowLink> getLinks() {
        return links;
    }

    private record Wire(float[] points, FlowLink[] members, float origin, boolean arrow, float tipX, float tipY, int dirX, int dirY) {

        FlowState state() {
            return worstOf(members);
        }
    }

    private record Junction(float x, float y, FlowLink[] members) {}

    private static FlowState worstOf(FlowLink[] members) {
        var state = FlowState.DISABLED;
        for (var member : members) state = FlowState.worst(state, member.getFlowState());
        return state;
    }

    private int left(FlowNode node) {
        return node.getPositionX() - getPositionX();
    }

    private int top(FlowNode node) {
        return node.getPositionY() - getPositionY();
    }

    private int centerX(FlowNode node) {
        return left(node) + node.getSizeWidth() / 2;
    }

    private int rowTop(int row) {
        int value = Integer.MAX_VALUE;
        for (var node : nodes) if (node.row == row) value = Math.min(value, top(node));
        return value;
    }

    private int rowBottom(int row) {
        int value = Integer.MIN_VALUE;
        for (var node : nodes) if (node.row == row) value = Math.max(value, top(node) + node.getSizeHeight());
        return value;
    }

    private boolean updateGeometry() {
        int size = nodes.size() * 4;
        boolean changed = !wiresValid;
        if (geometry.length != size) {
            geometry = new int[size];
            changed = true;
        }
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);
            int x = left(node), y = top(node), w = node.getSizeWidth(), h = node.getSizeHeight();
            int base = i * 4;
            if (geometry[base] != x || geometry[base + 1] != y || geometry[base + 2] != w || geometry[base + 3] != h) {
                geometry[base] = x;
                geometry[base + 1] = y;
                geometry[base + 2] = w;
                geometry[base + 3] = h;
                changed = true;
            }
        }
        return changed;
    }

    private void rebuildWires() {
        wires.clear();
        junctions.clear();
        wiresValid = true;
        var vertical = new ArrayList<FlowLink>(links.size());
        var bypasses = new ArrayList<FlowLink>();
        for (var link : links) {
            int span = Math.abs(link.to.row - link.from.row);
            if (span == 0) addHorizontal(link);
            else if (span == 1) vertical.add(link);
            else bypasses.add(link);
        }
        for (int channel = 0; channel + 1 < rows.size(); channel++) {
            var members = new ArrayList<FlowLink>();
            for (var link : vertical) if (Math.min(link.from.row, link.to.row) == channel) members.add(link);
            var detours = new ArrayList<FlowLink>();
            for (var link : bypasses) if (entryChannel(link) == channel) detours.add(link);
            if (!members.isEmpty() || !detours.isEmpty()) addChannel(channel, members, detours);
        }
    }

    private static int entryChannel(FlowLink link) {
        return link.to.row > link.from.row ? link.to.row - 1 : link.to.row;
    }

    private void addHorizontal(FlowLink link) {
        var from = link.from;
        var to = link.to;
        int dir = to.column >= from.column ? 1 : -1;
        int fromTop = top(from), fromBottom = fromTop + from.getSizeHeight();
        int toTop = top(to), toBottom = toTop + to.getSizeHeight();
        int sx = dir > 0 ? left(from) + from.getSizeWidth() : left(from);
        int tx = dir > 0 ? left(to) : left(to) + to.getSizeWidth();
        int overlapTop = Math.max(fromTop, toTop), overlapBottom = Math.min(fromBottom, toBottom);
        int sy, ty;
        if (overlapBottom > overlapTop) {
            sy = ty = (overlapTop + overlapBottom) / 2;
        } else {
            sy = (fromTop + fromBottom) / 2;
            ty = (toTop + toBottom) / 2;
        }
        float base = tx - dir * UISizes.FLOW_ARROW_SIZE;
        var points = CanvasRoute.horizontal(sx, sy, base, ty, (sx + base) / 2f);
        wires.add(new Wire(points, new FlowLink[] { link }, 0, true, tx, ty, dir, 0));
    }

    private void addChannel(int channel, List<FlowLink> members, List<FlowLink> detours) {
        var remaining = new ArrayList<>(members);
        var merges = new ArrayList<List<FlowLink>>();
        var fans = new ArrayList<List<FlowLink>>();
        for (var link : members) {
            if (!remaining.contains(link)) continue;
            var group = new ArrayList<FlowLink>();
            for (var other : remaining) if (other.to == link.to) group.add(other);
            if (group.size() >= 2) {
                merges.add(group);
                remaining.removeAll(group);
            }
        }
        for (var link : members) {
            if (!remaining.contains(link)) continue;
            var group = new ArrayList<FlowLink>();
            for (var other : remaining) if (other.from == link.from) group.add(other);
            if (group.size() >= 2) {
                fans.add(group);
                remaining.removeAll(group);
            }
        }
        int lanes = merges.size() + fans.size() + detours.size();
        for (var link : remaining) if (centerX(link.from) != centerX(link.to)) lanes++;
        int top = rowBottom(channel), bottom = rowTop(channel + 1);
        if (bottom <= top) bottom = top + UISizes.FLOW_CHANNEL_WIDTH;
        int lane = 0;
        for (var group : merges) addMerge(group, laneY(top, bottom, lane++, lanes));
        for (var group : fans) addFan(group, laneY(top, bottom, lane++, lanes));
        for (var link : remaining) {
            boolean straight = centerX(link.from) == centerX(link.to);
            addSingle(link, straight ? (top + bottom) / 2 : laneY(top, bottom, lane++, lanes));
        }
        for (var link : detours) addDetour(link, laneY(top, bottom, lane++, lanes));
    }

    private void addDetour(FlowLink link, int laneY) {
        var from = link.from;
        var to = link.to;
        int sx = left(from), sy = top(from) + from.getSizeHeight() / 2;
        int gx = gutter > 0 ? UISizes.FLOW_PADDING + gutter / 2 : UISizes.FLOW_PADDING / 2;
        int tx = left(to) + Math.min(UISizes.FLOW_DETOUR_INSET, to.getSizeWidth() / 4);
        int ty = targetY(link), dir = direction(link);
        float base = ty - dir * UISizes.FLOW_ARROW_SIZE;
        float[] points = { sx, sy, gx, sy, gx, laneY, tx, laneY, tx, base };
        wires.add(new Wire(points, new FlowLink[] { link }, 0, true, tx, ty, 0, dir));
    }

    private static int laneY(int top, int bottom, int lane, int lanes) {
        return top + Math.round((bottom - top) * (lane + 1) / (float) (lanes + 1));
    }

    private int sourceY(FlowLink link) {
        boolean down = link.to.row > link.from.row;
        return down ? top(link.from) + link.from.getSizeHeight() : top(link.from);
    }

    private int targetY(FlowLink link) {
        boolean down = link.to.row > link.from.row;
        return down ? top(link.to) : top(link.to) + link.to.getSizeHeight();
    }

    private static int direction(FlowLink link) {
        return link.to.row > link.from.row ? 1 : -1;
    }

    private void addMerge(List<FlowLink> group, int laneY) {
        var first = group.get(0);
        int tx = centerX(first.to), ty = targetY(first), dir = direction(first);
        var all = group.toArray(new FlowLink[0]);
        for (var link : group) {
            int sx = centerX(link.from), sy = sourceY(link);
            float[] points = sx == tx ? new float[] { sx, sy, sx, laneY } : new float[] { sx, sy, sx, laneY, tx, laneY };
            wires.add(new Wire(points, new FlowLink[] { link }, -CanvasPainter.pathLength(points), false, 0, 0, 0, 0));
        }
        wires.add(new Wire(new float[] { tx, laneY, tx, ty - dir * UISizes.FLOW_ARROW_SIZE }, all, 0, true, tx, ty, 0, dir));
        junctions.add(new Junction(tx, laneY, all));
    }

    private void addFan(List<FlowLink> group, int laneY) {
        var first = group.get(0);
        int sx = centerX(first.from), sy = sourceY(first);
        var all = group.toArray(new FlowLink[0]);
        float[] stem = { sx, sy, sx, laneY };
        wires.add(new Wire(stem, all, -CanvasPainter.pathLength(stem), false, 0, 0, 0, 0));
        for (var link : group) {
            int tx = centerX(link.to), ty = targetY(link), dir = direction(link);
            float base = ty - dir * UISizes.FLOW_ARROW_SIZE;
            float[] points = sx == tx ? new float[] { sx, laneY, sx, base } : new float[] { sx, laneY, tx, laneY, tx, base };
            wires.add(new Wire(points, new FlowLink[] { link }, 0, true, tx, ty, 0, dir));
        }
        junctions.add(new Junction(sx, laneY, all));
    }

    private void addSingle(FlowLink link, int laneY) {
        int sx = centerX(link.from), sy = sourceY(link);
        int tx = centerX(link.to), ty = targetY(link), dir = direction(link);
        float base = ty - dir * UISizes.FLOW_ARROW_SIZE;
        float[] points = sx == tx ? new float[] { sx, sy, sx, base } : CanvasRoute.vertical(sx, sy, tx, base, laneY);
        wires.add(new Wire(points, new FlowLink[] { link }, 0, true, tx, ty, 0, dir));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        if (painter == null) painter = new CanvasPainter();
        if (updateGeometry()) rebuildWires();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        painter.beginScreen(graphics, CanvasRect.of(0, 0, w, h));
        drawWires(painter);
        painter.flush();
        pose.popPose();
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @OnlyIn(Dist.CLIENT)
    private void drawWires(CanvasPainter painter) {
        float width = UISizes.FLOW_WIRE_WIDTH;
        for (var wire : wires) CanvasWire.glow(painter, wire.points, width, wire.state().getWire());
        for (var level : STATES) {
            for (var wire : wires) {
                if (wire.state() == level) CanvasWire.core(painter, wire.points, width, level.getWire(), wire.origin);
            }
        }
        for (var wire : wires) CanvasWire.sparks(painter, wire.points, width, wire.state().getWire(), wire.origin);
        for (var junction : junctions) {
            painter.junction(junction.x, junction.y, UISizes.FLOW_JUNCTION_SIZE, worstOf(junction.members).getWire().core());
        }
        for (var wire : wires) {
            if (wire.arrow) painter.arrow(wire.tipX, wire.tipY, wire.dirX, wire.dirY, width, UISizes.FLOW_ARROW_SIZE, wire.state().getWire().core());
        }
    }
}
