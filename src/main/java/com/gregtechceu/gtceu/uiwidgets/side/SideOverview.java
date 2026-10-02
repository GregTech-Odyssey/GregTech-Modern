package com.gregtechceu.gtceu.uiwidgets.side;

import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputFluid;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputItem;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.Binding;
import com.gregtechceu.gtceu.uipro.data.ClientOnly;
import com.gregtechceu.gtceu.uipro.data.SyncItem;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.ButtonGroup;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.render.UILayers;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverTab;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SideOverview {

    static final MachineSide[] ORDER = { MachineSide.TOP, MachineSide.BOTTOM, MachineSide.FRONT, MachineSide.BACK, MachineSide.LEFT,
            MachineSide.RIGHT };
    static final int NO_FACE = 6;
    static final int AUTO = 8;
    private static final MachineSide[] SIDES = MachineSide.values();
    private static final int CROSS_SIZE = 3 * UISizes.SLOT_SIZE + 2 * UISizes.GAP;
    private static final int SCENE_WIDTH = UISizes.CONTENT_WIDTH - CROSS_SIZE - UISizes.SECTION_GAP;
    private static final int SCENE_HEIGHT = 4 * UISizes.SLOT_SIZE;
    private static final int NONE = 0, OUTPUT = 1, AUTO_OUTPUT = 2;
    private static final int KEY_SPAN = 64;
    private static final String[] MODES = { "gtceu.gui.side_overview.mode.none", "gtceu.gui.side_overview.mode.output",
            "gtceu.gui.side_overview.mode.auto" };
    private static final List<List<Component>> MODE_TOOLTIPS = List.of(
            List.of(Component.translatable("gtceu.gui.side_overview.mode.none.tooltip")),
            List.of(Component.translatable("gtceu.gui.side_overview.mode.output.tooltip")),
            List.of(Component.translatable("gtceu.gui.side_overview.mode.auto.tooltip")));
    private static final int MARK = 4;
    private static final String FACE = "gtceu.gui.output_side.face";
    private static final String NO_COVER = "gtceu.gui.side_overview.no_cover";
    private static final String ITEM_OUTPUT = "gtceu.gui.side_overview.item_output";
    private static final String FLUID_OUTPUT = "gtceu.gui.side_overview.fluid_output";
    private static final String AUTO_ON = "gtceu.gui.side_overview.auto_on";
    private static final String AUTO_OFF = "gtceu.gui.side_overview.auto_off";
    private static final String SHOW_FACE = "gtceu.gui.side_overview.show_face";
    private static final String OPEN_COVER = "gtceu.gui.side_overview.open_cover";
    private static final String NO_COVER_SETTINGS = "gtceu.gui.side_overview.no_cover_settings";
    private static final String OPEN_COVER_SHORT = "gtceu.gui.side_overview.open_cover_short";
    private static final String NO_SELECTION = "gtceu.gui.side_overview.no_selection";
    private static final String NONE_SELECTED = "gtceu.gui.side_overview.none_selected";
    private static final String SELECTED_COVER = "gtceu.gui.side_overview.selected_cover";
    private static final String ITEM_MODE = "gtceu.gui.side_overview.item_mode";
    private static final String FLUID_MODE = "gtceu.gui.side_overview.fluid_mode";
    private static final String FRONT = "gtceu.gui.output_side.front";

    final MetaMachine machine;
    @Nullable
    private final IAutoOutputItem items;
    @Nullable
    private final IAutoOutputFluid fluids;
    private final ClientOnly<SideScene> scene = ClientOnly.empty();
    private final CoverBehavior[] seenCovers = new CoverBehavior[ORDER.length];
    private int coverVersion;
    private final SyncValue<Integer> frame;
    private final SyncValue<Integer> itemOutput;
    private final SyncValue<Integer> fluidOutput;
    private final SyncValue<Integer> covers;
    private final FaceCell[] cells = new FaceCell[MachineSide.values().length];
    @SuppressWarnings("unchecked")
    private final List<Component>[] tooltips = new List[MachineSide.values().length];
    private int serverSelected = -1;
    @Nullable
    private Binding<Integer> selection;
    private int selectedKey = Integer.MIN_VALUE;
    @Nullable
    private CoverBehavior selectedTextCover;
    private Component selectedText = Component.empty();
    private int cellHover = -1;
    private int sceneHover = -1;

    private SideOverview(MetaMachine machine) {
        this.machine = machine;
        this.items = machine instanceof IAutoOutputItem item && item.hasAutoOutputItem() ? item : null;
        this.fluids = machine instanceof IAutoOutputFluid fluid && fluid.hasAutoOutputFluid() ? fluid : null;
        this.frame = SyncValue.ofInt(() -> MachineSide.frameKey(machine), 0).onChanged(v -> onWorldChanged());
        this.itemOutput = SyncValue.ofInt(this::itemState, NO_FACE).onChanged(v -> invalidateTooltips());
        this.fluidOutput = SyncValue.ofInt(this::fluidState, NO_FACE).onChanged(v -> invalidateTooltips());
        this.covers = SyncValue.ofInt(this::coverVersion, 0).onChanged(v -> onWorldChanged());
    }

    public static Widget createPage(MetaMachine machine) {
        return new SideOverview(machine).build();
    }

    private Widget build() {
        var content = UIElement.column(UISizes.CONTENT_WIDTH).layout(l -> l.gapAll(UISizes.SECTION_GAP));
        content.addSyncValue(frame);
        content.addSyncValue(itemOutput);
        content.addSyncValue(fluidOutput);
        content.addSyncValue(covers);
        selection = content.getChannel().addBinding(Binding.bindInt(() -> serverSelected, v -> serverSelected = v, -1, SIDES.length - 1));
        var holder = new UIElement().layout(l -> l.size(SCENE_WIDTH, SCENE_HEIGHT));
        if (machine.isRemote()) addScene(holder);
        var cross = UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP))
                .addChildren(crossRow(null, MachineSide.TOP, null), crossRow(MachineSide.LEFT, MachineSide.FRONT, MachineSide.RIGHT),
                        crossRow(null, MachineSide.BOTTOM, MachineSide.BACK));
        content.addChild(UIElement.row(SCENE_HEIGHT).layout(l -> l.width(UISizes.CONTENT_WIDTH).gapAll(UISizes.SECTION_GAP).alignCenter())
                .addChildren(holder, cross));
        content.addChild(faceSettings());
        var scroller = ScrollerView.page("side_overview.page", UISizes.CONTENT_WIDTH).adaptiveWidth().addScrollViewChild(content);
        return UIElement.column(LayoutStyle.AUTO).addChild(scroller);
    }

    @OnlyIn(Dist.CLIENT)
    private void addScene(UIElement holder) {
        var created = new SideScene(this, SCENE_WIDTH, SCENE_HEIGHT);
        scene.set(created);
        holder.addChild(created.getView());
    }

    private UIElement crossRow(@Nullable MachineSide left, MachineSide middle, @Nullable MachineSide right) {
        return UIElement.row(UISizes.SLOT_SIZE).layout(l -> l.gapAll(UISizes.GAP)).addChildren(cell(left), cell(middle), cell(right));
    }

    private Widget cell(@Nullable MachineSide side) {
        if (side == null) return UIElement.spacer(UISizes.SLOT_SIZE, UISizes.SLOT_SIZE);
        var cell = new FaceCell(this, side);
        cells[side.ordinal()] = cell;
        return cell;
    }

    private UIElement faceSettings() {
        var section = UIElement.section();
        section.disabled(() -> selectedSide() == null, NO_SELECTION);
        var name = TextLine.of(0, this::selectedText).bindClientColor(UITheme::panelText);
        name.layout(l -> l.flex(1));
        var open = Button.translatable(UISizes.BUTTON_WIDTH, OPEN_COVER_SHORT).tooltips(OPEN_COVER).setOnClientClick(this::openSelectedCover);
        open.disabled(() -> !(selectedCover() instanceof IUICover), NO_COVER_SETTINGS);
        section.addChild(UIElement.centeredRow(UISizes.CONTROL_HEIGHT).addChildren(name, open));
        if (items != null) section.addChild(Form.controlRow(ITEM_MODE, modeGroup(false)).disabled(this::isSelectedFront, FRONT));
        if (fluids != null) section.addChild(Form.controlRow(FLUID_MODE, modeGroup(true)).disabled(this::isSelectedFront, FRONT));
        return section;
    }

    private ButtonGroup modeGroup(boolean fluid) {
        return ButtonGroup.single(MODES.length, i -> Component.translatable(MODES[i]), () -> mode(fluid), i -> setMode(fluid, i))
                .compact().optionTooltips(MODE_TOOLTIPS::get);
    }

    @Nullable
    private MachineSide selectedSide() {
        return serverSelected >= 0 && serverSelected < SIDES.length ? SIDES[serverSelected] : null;
    }

    @Nullable
    private CoverBehavior selectedCover() {
        var side = selectedSide();
        return side == null ? null : coverAt(side);
    }

    private boolean isSelectedFront() {
        var side = selectedSide();
        return side != null && isFront(side.toDirection(machine));
    }

    private boolean isFront(Direction direction) {
        return machine.hasFrontFacing() && machine.getFrontFacing() == direction;
    }

    private Component selectedText() {
        var side = selectedSide();
        var cover = side == null ? null : coverAt(side);
        int key = side == null ? -1 : side.ordinal() * KEY_SPAN + MachineSide.frameKey(machine);
        if (key == selectedKey && cover == selectedTextCover) return selectedText;
        selectedKey = key;
        selectedTextCover = cover;
        if (side == null) {
            selectedText = Component.translatable(NONE_SELECTED);
        } else {
            var face = Component.translatable(FACE, Component.translatable(side.translationKey),
                    Component.translatable(FaceNet.nameKey(side.toDirection(machine))));
            selectedText = Component.translatable(SELECTED_COVER, face,
                    cover == null ? Component.translatable(NO_COVER) : cover.getAttachItem().getHoverName());
        }
        return selectedText;
    }

    private int mode(boolean fluid) {
        var side = selectedSide();
        if (side == null) return NONE;
        Direction face;
        boolean auto;
        if (fluid) {
            if (fluids == null) return NONE;
            face = fluids.getOutputFacingFluids();
            auto = fluids.isAutoOutputFluids();
        } else {
            if (items == null) return NONE;
            face = items.getOutputFacingItems();
            auto = items.isAutoOutputItems();
        }
        if (face != side.toDirection(machine)) return NONE;
        return auto ? AUTO_OUTPUT : OUTPUT;
    }

    private void setMode(boolean fluid, int mode) {
        var side = selectedSide();
        if (side == null) return;
        var direction = side.toDirection(machine);
        if (isFront(direction)) return;
        if (fluid) {
            if (fluids == null) return;
            if (mode == NONE) {
                if (fluids.getOutputFacingFluids() != direction) return;
                fluids.setAutoOutputFluids(false);
                fluids.setOutputFacingFluids(null);
            } else {
                fluids.setOutputFacingFluids(direction);
                fluids.setAutoOutputFluids(mode == AUTO_OUTPUT);
            }
        } else {
            if (items == null) return;
            if (mode == NONE) {
                if (items.getOutputFacingItems() != direction) return;
                items.setAutoOutputItems(false);
                items.setOutputFacingItems(null);
            } else {
                items.setOutputFacingItems(direction);
                items.setAutoOutputItems(mode == AUTO_OUTPUT);
            }
        }
        machine.requestSync();
    }

    private void openSelectedCover() {
        int index = getSelected();
        if (index >= 0 && index < SIDES.length) openCover(SIDES[index]);
    }

    @Nullable
    private CoverBehavior coverAt(MachineSide side) {
        return machine.getCoverContainer().getCoverAtSide(side.toDirection(machine));
    }

    private ItemStack coverStack(MachineSide side) {
        var cover = coverAt(side);
        return cover == null ? ItemStack.EMPTY : cover.getAttachItem();
    }

    private int itemState() {
        if (items == null) return NO_FACE;
        var face = items.getOutputFacingItems();
        return face == null ? NO_FACE : face.ordinal() | (items.isAutoOutputItems() ? AUTO : 0);
    }

    private int fluidState() {
        if (fluids == null) return NO_FACE;
        var face = fluids.getOutputFacingFluids();
        return face == null ? NO_FACE : face.ordinal() | (fluids.isAutoOutputFluids() ? AUTO : 0);
    }

    private int coverVersion() {
        var container = machine.getCoverContainer();
        for (int i = 0; i < ORDER.length; i++) {
            var cover = container.getCoverAtSide(ORDER[i].toDirection(machine));
            if (cover != seenCovers[i]) {
                seenCovers[i] = cover;
                coverVersion++;
            }
        }
        return coverVersion;
    }

    private void openCover(MachineSide side) {
        var window = MachineWindow.of(cells[side.ordinal()]);
        if (window == null) return;
        var direction = direction(side);
        for (var tab : window.getTabs()) {
            if (tab instanceof CoverTab coverTab && coverTab.getDirection() == direction) {
                window.selectTab(tab);
                return;
            }
        }
    }

    private void onWorldChanged() {
        invalidateTooltips();
        if (machine.isRemote()) reloadScene();
    }

    @OnlyIn(Dist.CLIENT)
    private void reloadScene() {
        var current = scene.get();
        if (current != null) current.reload();
    }

    private void invalidateTooltips() {
        for (int i = 0; i < tooltips.length; i++) tooltips[i] = null;
    }

    Direction direction(MachineSide side) {
        return side.toDirection(frame.getValue());
    }

    MachineSide side(Direction direction) {
        return MachineSide.of(frame.getValue(), direction);
    }

    @Nullable
    static Direction outputFace(int state) {
        int face = state & 7;
        return face < NO_FACE ? Direction.values()[face] : null;
    }

    static boolean isAuto(int state) {
        return (state & AUTO) != 0;
    }

    int getItemOutput() {
        return itemOutput.getValue();
    }

    int getFluidOutput() {
        return fluidOutput.getValue();
    }

    boolean isItemOutput(MachineSide side) {
        return outputFace(itemOutput.getValue()) == direction(side);
    }

    boolean isFluidOutput(MachineSide side) {
        return outputFace(fluidOutput.getValue()) == direction(side);
    }

    int getSelected() {
        return selection == null ? -1 : selection.getValue();
    }

    int getHighlighted() {
        return cellHover >= 0 ? cellHover : sceneHover;
    }

    void setSceneHover(@Nullable MachineSide side) {
        sceneHover = side == null ? -1 : side.ordinal();
    }

    void select(MachineSide side) {
        if (selection != null) selection.set(side.ordinal());
    }

    @OnlyIn(Dist.CLIENT)
    private void clientSelect(MachineSide side) {
        if (selection == null) return;
        if (getSelected() == side.ordinal()) {
            selection.set(-1);
            return;
        }
        selection.set(side.ordinal());
        var current = scene.get();
        if (current != null) current.lookAt(side);
    }

    List<Component> tooltip(MachineSide side) {
        var cached = tooltips[side.ordinal()];
        if (cached != null) return cached;
        var lines = new ArrayList<Component>(4);
        var direction = direction(side);
        lines.add(Component.translatable(FACE, Component.translatable(side.translationKey), Component.translatable(FaceNet.nameKey(direction))));
        var cell = cells[side.ordinal()];
        var stack = cell == null ? ItemStack.EMPTY : cell.getStack();
        lines.add(stack.isEmpty() ? Component.translatable(NO_COVER).withStyle(ChatFormatting.GRAY) : stack.getHoverName().copy().withStyle(ChatFormatting.AQUA));
        if (isItemOutput(side)) lines.add(outputLine(ITEM_OUTPUT, itemOutput.getValue()));
        if (isFluidOutput(side)) lines.add(outputLine(FLUID_OUTPUT, fluidOutput.getValue()));
        var list = Collections.unmodifiableList(lines);
        tooltips[side.ordinal()] = list;
        return list;
    }

    private static Component outputLine(String key, int state) {
        return Component.translatable(key, Component.translatable(isAuto(state) ? AUTO_ON : AUTO_OFF)).withStyle(ChatFormatting.GOLD);
    }

    private static final class FaceCell extends UIElement {

        private static final int SIZE = UISizes.SLOT_SIZE;

        private final SideOverview page;
        private final MachineSide side;
        private final SyncValue<SyncItem> item;
        @Nullable
        private List<Component> hoverSource;
        private List<Component> hoverLines = Collections.emptyList();

        private FaceCell(SideOverview page, MachineSide side) {
            this.page = page;
            this.side = side;
            layout(l -> l.size(SIZE, SIZE));
            this.item = addSyncValue(SyncValue.ofItem(() -> page.coverStack(side)).onChanged(v -> page.invalidateTooltips()));
        }

        private ItemStack getStack() {
            return item.getValue().stack();
        }

        @Override
        public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
            if (isMouseOverElement(mouseX, mouseY) && !getStack().isEmpty()) return getStack();
            return super.getXEIIngredientOverMouse(mouseX, mouseY);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !isMouseOverElement(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
            page.clientSelect(side);
            playButtonClickSound();
            return true;
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x = getPositionX(), y = getPositionY();
            boolean over = isMouseOverElement(mouseX, mouseY);
            if (over) page.cellHover = side.ordinal();
            else if (page.cellHover == side.ordinal()) page.cellHover = -1;
            UITheme.ITEM_SLOT.draw(graphics, mouseX, mouseY, x, y, SIZE, SIZE);
            var stack = getStack();
            if (!stack.isEmpty()) graphics.renderItem(stack, x + 1, y + 1);
            if (over || page.sceneHover == side.ordinal()) UIDraw.hoverOverlay(graphics, x, y, SIZE, SIZE);
            boolean itemOut = page.isItemOutput(side), fluidOut = page.isFluidOutput(side);
            if (itemOut || fluidOut) {
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(0, 0, UILayers.ITEM_OVERLAY);
                if (itemOut) UIDraw.lamp(graphics, x + 1, y + SIZE - 1 - MARK, MARK, UITheme.SIDE_ITEM_OUTPUT);
                if (fluidOut) UIDraw.lamp(graphics, x + SIZE - 1 - MARK, y + SIZE - 1 - MARK, MARK, UITheme.SIDE_FLUID_OUTPUT);
                pose.popPose();
            }
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if (page.getSelected() == side.ordinal()) UIDraw.selectionFrame(graphics, getPositionX(), getPositionY(), SIZE, SIZE);
            if (gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
            var lines = page.tooltip(side);
            if (hoverSource != lines) {
                var withHint = new ArrayList<Component>(lines.size() + 1);
                withHint.addAll(lines);
                withHint.add(Component.translatable(SHOW_FACE).withStyle(ChatFormatting.DARK_GRAY));
                hoverSource = lines;
                hoverLines = withHint;
            }
            gui.getModularUIGui().setHoverTooltip(hoverLines, ItemStack.EMPTY, null, null);
        }
    }
}
