package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Layout;
import com.gregtechceu.gtceu.api.machine.multiblockpro.MachineProtocol;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Structure;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureTree;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ButtonGroup;
import com.gregtechceu.gtceu.uipro.elements.ItemView;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.flow.FlowChart;
import com.gregtechceu.gtceu.uipro.flow.FlowNode;
import com.gregtechceu.gtceu.uipro.flow.FlowState;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.view.FlowView;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public final class StructureConfigView extends UIElement implements ILocalUI {

    public static final String SEGMENT = "gtceu.structure_config.segment";
    public static final String SUMMARY = "gtceu.structure_config.summary";
    public static final String INVALID = "gtceu.structure_config.invalid";
    public static final String ROOT = "gtceu.structure_config.root";
    public static final String PART = "gtceu.structure_config.part";
    public static final String OPTIONAL = "gtceu.structure_config.optional";
    public static final String CHOICE = "gtceu.structure_config.choice";
    public static final String BRANCH = "gtceu.structure_config.branch";
    public static final String NONE = "gtceu.structure_config.none";
    public static final String ENABLED = "gtceu.structure_config.enabled";
    public static final String REPEAT = "gtceu.structure_config.repeat";
    public static final String INACTIVE = "gtceu.structure_config.inactive";
    public static final String MACHINES = "gtceu.structure_config.machines";
    public static final String MACHINES_ACCEPT = "gtceu.structure_config.machines.accept";
    public static final String MACHINES_OPEN = "gtceu.structure_config.machines.open";
    public static final String HOSTS = "gtceu.structure_config.hosts";
    public static final String LISTED = "gtceu.structure_config.listed";
    public static final String HOSTS_PORT = "gtceu.structure_config.hosts.port";

    private static final int COLUMN = 104;
    private static final long[] STEPS = { 1, 4 };

    private final Structure structure;
    private final StructureTree tree;
    private final int[] values;
    @Nullable
    private final boolean[] excluded;
    private final Runnable onChange;
    @Nullable
    private final Consumer<MultiblockMachineDefinition> onOpen;
    private final List<MachineProtocol> mounted = new ArrayList<>();
    @Nullable
    private Layout layout;
    @Nullable
    private Layout listed;
    private int blocks;
    @Nullable
    private FlowView view;
    private final int viewWidth;
    private int viewMaxHeight;

    public StructureConfigView(MultiblockMachineDefinition definition, Structure structure, int[] values, @Nullable boolean[] excluded,
                               Runnable onChange, @Nullable Consumer<MultiblockMachineDefinition> onOpen, int minWidth, int viewWidth, int viewHeight) {
        this.structure = structure;
        this.tree = structure.tree();
        this.values = values;
        this.excluded = excluded;
        this.onChange = onChange;
        this.onOpen = onOpen;
        this.viewWidth = viewWidth;
        for (var protocol : definition.getMountedOn()) {
            if (!StructureHosts.of(protocol).isEmpty()) mounted.add(protocol);
        }
        setClientSideWidget();
        layout(l -> l.column().width(LayoutStyle.AUTO).gapAll(UISizes.GAP));
        if (tree.nodes().length > 1 || tree.sizes().length > 0 || !mounted.isEmpty()) {
            viewMaxHeight = Math.max(UISizes.SLOT_SIZE * 2, viewHeight - TextLine.HEIGHT - UISizes.GAP);
            view = new FlowView("structure_config", chart(), false).maxSize(viewWidth, viewMaxHeight).minSize(minWidth, 0);
            addChild(view);
        }
        addChild(TextLine.of(LayoutStyle.AUTO, this::summary).bindLevel(() -> layout == null ? Level.ERROR : Level.NORMAL));
        refresh();
    }

    private FlowChart chart() {
        var nodes = tree.nodes();
        var layout = FlowChart.tree(nodes.length, 0, index -> nodes[index].children());
        var widths = new int[layout.columns()];
        Arrays.fill(widths, COLUMN);
        var chart = new FlowChart(0, widths);
        int offset = mounted.isEmpty() ? 0 : 1;
        var flowNodes = new FlowNode[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            flowNodes[i] = node(chart, i, layout.row()[i] + offset, layout.column()[i], layout.span()[i]);
            int parent = nodes[i].parent();
            if (parent >= 0) chart.link(flowNodes[parent], flowNodes[i]).follow(flowNodes[i]);
        }
        if (offset > 0) chart.link(hosts(chart, layout.columns()), flowNodes[0]).follow(flowNodes[0]);
        return chart;
    }

    private FlowNode hosts(FlowChart chart, int span) {
        var flow = chart.node(0, 0, span).bindState(() -> FlowState.READY);
        flow.addChild(TextLine.translatable(LayoutStyle.AUTO, HOSTS));
        for (var protocol : mounted) {
            flow.addChild(TextLine.constant(LayoutStyle.AUTO, Component.translatable(HOSTS_PORT, protocol.getName())).setColor(UITheme.PANEL_TEXT));
            for (var host : StructureHosts.of(protocol)) flow.addChild(machineRow(host));
        }
        return flow;
    }

    private Widget machineRow(MachineDefinition machine) {
        if (onOpen != null && machine instanceof MultiblockMachineDefinition multiblock && multiblock.hasStructure()) {
            return new MachineLink(multiblock, () -> onOpen.accept(multiblock));
        }
        return UIElement.centeredRow(UISizes.CONTROL_HEIGHT)
                .addChildren(ItemView.of(machine.asStack()), TextLine.constant(0, machine.asStack().getHoverName()).layout(l -> l.flex(1)));
    }

    private FlowNode node(FlowChart chart, int index, int row, int column, int span) {
        var node = tree.nodes()[index];
        int inner = chart.widthFor(column, span) - 2 * UISizes.FLOW_NODE_PADDING;
        var flow = chart.node(row, column, span).bindState(() -> state(index));
        var key = node.key();
        if (key != null && key.getDescriptionKey() != null) flow.bindDetail(() -> Collections.singletonList(Component.translatable(key.getDescriptionKey())));
        flow.addChild(TextLine.constant(LayoutStyle.AUTO, title(index)));
        switch (node.kind()) {
            case ROOT -> {
                var options = structure.options();
                for (int option : tree.sizes()) {
                    var size = options.get(option).key();
                    flow.addChild(TextLine.constant(LayoutStyle.AUTO, size == null ? Component.translatable(SEGMENT) : size.getName()));
                    flow.addChild(field(index, option, inner, options.get(option).min(), options.get(option).max()));
                }
            }
            case FIXED -> {
                if (node.max() > 1) flow.addChild(TextLine.constant(LayoutStyle.AUTO, Component.translatable(REPEAT, node.max())));
            }
            case TOGGLE -> {
                var toggle = Switch.of(() -> values[node.option()] > 0, on -> set(node.option(), on ? 1 : 0))
                        .disabled(() -> !tree.active(index, values), INACTIVE);
                flow.addChild(UIElement.centeredRow(UISizes.CONTROL_HEIGHT)
                        .addChildren(TextLine.translatable(0, ENABLED).layout(l -> l.flex(1)), toggle));
            }
            case COUNT -> flow.addChild(field(index, node.option(), inner, node.min(), node.max()));
            case CHOICE -> {
                if (node.option() >= 0) flow.addChild(choices(index, node));
            }
            default -> {}
        }
        switch (node.kind()) {
            case ROOT, FIXED, TOGGLE, COUNT -> {
                if (excluded != null && index < excluded.length) {
                    var listed = Switch.of(() -> !excluded[index], on -> setListed(index, on)).disabled(() -> !tree.active(index, values), INACTIVE);
                    flow.addChild(UIElement.centeredRow(UISizes.CONTROL_HEIGHT)
                            .addChildren(TextLine.translatable(0, LISTED).layout(l -> l.flex(1)), listed));
                }
            }
            default -> {}
        }
        switch (node.kind()) {
            case MACHINES -> {
                if (node.protocol() != null) {
                    flow.addChild(TextLine.translatable(LayoutStyle.AUTO, MACHINES_ACCEPT).setColor(UITheme.PANEL_TEXT));
                    for (var member : node.protocol().getMembers()) flow.addChild(machineRow(member));
                }
            }
        }
        return flow;
    }

    private UIElement field(int index, int option, int width, int min, int max) {
        var field = NumberField.ofInt(width, () -> values[option], value -> set(option, value), min, max).setSteps(STEPS);
        field.disabled(() -> !tree.active(index, values), INACTIVE);
        return field;
    }

    private ButtonGroup choices(int index, StructureTree.Node node) {
        int offset = node.min() == 0 ? 1 : 0;
        var branches = node.children();
        var group = ButtonGroup.single(branches.length + offset,
                i -> i < offset ? Component.translatable(NONE) : title(branches[i - offset]),
                () -> values[node.option()] - 1 + offset,
                i -> set(node.option(), i + 1 - offset));
        group.disabled(() -> !tree.active(index, values), INACTIVE);
        return group;
    }

    private Component title(int index) {
        return nodeTitle(tree, index);
    }

    public static Component nodeTitle(StructureTree tree, int index) {
        if (index < 0 || index >= tree.nodes().length) return Component.translatable(PART);
        var node = tree.nodes()[index];
        if (node.key() != null) return node.key().getName();
        if (node.protocol() != null) return node.protocol().getName();
        if (node.branch() >= 0) return Component.translatable(BRANCH, node.branch() + 1);
        return Component.translatable(switch (node.kind()) {
            case ROOT -> ROOT;
            case FIXED -> PART;
            case TOGGLE -> OPTIONAL;
            case COUNT -> SEGMENT;
            case CHOICE -> CHOICE;
            case MACHINES -> MACHINES;
        });
    }

    private FlowState state(int index) {
        if (!tree.active(index, values)) return FlowState.DISABLED;
        var node = tree.nodes()[index];
        return switch (node.kind()) {
            case ROOT, FIXED, MACHINES -> FlowState.READY;
            case TOGGLE, COUNT, CHOICE -> tree.value(index, values) > 0 ? FlowState.READY : FlowState.IDLE;
        };
    }

    public int viewHeight() {
        return view == null ? 0 : view.getSizeHeight();
    }

    public void fitViewHeight(int height) {
        int max = Math.max(UISizes.SLOT_SIZE * 2, height);
        if (view == null || max == viewMaxHeight) return;
        viewMaxHeight = max;
        view.maxSize(viewWidth, max);
    }

    @Nullable
    public Layout currentLayout() {
        return layout;
    }

    @Nullable
    public Layout listLayout() {
        if (layout == null || excluded == null) return layout;
        if (listed == null) listed = layout.exclude(excluded);
        return listed;
    }

    private void setListed(int index, boolean listed) {
        if (excluded == null || excluded[index] == !listed) return;
        excluded[index] = !listed;
        this.listed = null;
        onChange.run();
    }

    private void set(int index, int value) {
        if (values[index] == value) return;
        int previous = values[index];
        values[index] = value;
        if (excluded != null) followBuild(index, previous, value);
        refresh();
        onChange.run();
    }

    public static boolean[] defaultExcluded(StructureTree tree, int[] values) {
        var nodes = tree.nodes();
        var excluded = new boolean[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            var node = nodes[i];
            int option = node.option();
            if (option < 0 || option >= values.length) continue;
            switch (node.kind()) {
                case TOGGLE, COUNT -> excluded[i] = values[option] == 0;
                case CHOICE -> {
                    for (int child : node.children()) excluded[child] = nodes[child].branch() != values[option] - 1;
                }
                default -> {}
            }
        }
        return excluded;
    }

    private void followBuild(int option, int previous, int value) {
        var nodes = tree.nodes();
        for (int i = 0; i < nodes.length && i < excluded.length; i++) {
            var node = nodes[i];
            if (node.option() != option) continue;
            switch (node.kind()) {
                case TOGGLE, COUNT -> {
                    if ((previous > 0) != (value > 0)) excluded[i] = value == 0;
                }
                case CHOICE -> {
                    for (int child : node.children()) {
                        if (child < excluded.length) excluded[child] = nodes[child].branch() != value - 1;
                    }
                }
                default -> {}
            }
        }
    }

    private void refresh() {
        layout = structure.layout(values);
        listed = null;
        blocks = 0;
        if (layout == null) return;
        var placeable = new Reference2BooleanOpenHashMap<TraceabilityPredicate>();
        var cells = layout.cells();
        for (int i = 0; i < cells.size(); i++) {
            var predicate = cells.get(i).predicate();
            boolean counted;
            if (placeable.containsKey(predicate)) {
                counted = placeable.getBoolean(predicate);
            } else {
                counted = placeable(predicate);
                placeable.put(predicate, counted);
            }
            if (counted) blocks++;
        }
    }

    private static boolean placeable(TraceabilityPredicate predicate) {
        var found = new boolean[1];
        predicate.forEachSimple(simple -> {
            if (simple != null && simple.candidates != null) found[0] = true;
        });
        return found[0];
    }

    private Component summary() {
        if (layout == null) return Component.translatable(INVALID);
        return Component.translatable(SUMMARY, layout.width(), layout.height(), layout.depth(), blocks);
    }

    private static final class MachineLink extends UIElement {

        private static final int ARROW_WIDTH = 4;

        private final Runnable onClick;

        private MachineLink(MultiblockMachineDefinition definition, Runnable onClick) {
            this.onClick = onClick;
            var stack = definition.asStack();
            layout(l -> l.row().height(UISizes.CONTROL_HEIGHT).gapAll(UISizes.GAP).alignCenter().paddingRight(ARROW_WIDTH + UISizes.GAP));
            addChildren(ItemView.of(stack), TextLine.constant(0, stack.getHoverName()).setColor(UITheme.LINK_TEXT).layout(l -> l.flex(1)));
            setHoverTooltips(stack.getHoverName(), Component.translatable(MACHINES_OPEN));
        }

        @Override
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x = getPositionX(), y = getPositionY(), width = getSizeWidth(), height = getSizeHeight();
            if (isMouseOverElement(mouseX, mouseY)) graphics.fill(x, y, x + width, y + height, UITheme.SEGMENT_HOVER);
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
            UITheme.LINK_ARROW.draw(graphics, mouseX, mouseY, x + width - ARROW_WIDTH, y, ARROW_WIDTH, height);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && isMouseOverElement(mouseX, mouseY)) {
                playButtonClickSound();
                onClick.run();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }
}
