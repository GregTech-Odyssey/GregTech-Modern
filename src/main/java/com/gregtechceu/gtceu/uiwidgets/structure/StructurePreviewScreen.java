package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Layout;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Structure;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructurePattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.uipro.ILayoutHost;
import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.ItemView;
import com.gregtechceu.gtceu.uipro.elements.Stepper;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.utils.UIPreferences;
import com.gregtechceu.gtceu.uipro.view.ZoomBar;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;
import com.gregtechceu.gtceu.uipro.window.Popup;
import com.gregtechceu.gtceu.uipro.window.PopupCard;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.uiwidgets.patternbuilder.PatternBuilderModel;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public final class StructurePreviewScreen extends ModularUIGuiContainer {

    public static final String TITLE = "gtceu.structure_preview.title";
    public static final String CONFIG = "gtceu.structure_preview.config";
    public static final String PARTS = "gtceu.structure_preview.parts";
    public static final String PARTS_FILTER = "gtceu.structure_preview.parts.filter";
    public static final String LAYER = "gtceu.structure_preview.layer";
    public static final String LAYER_ALL = "gtceu.structure_preview.layer.all";
    public static final String LAYER_N = "gtceu.structure_preview.layer.n";
    public static final String OPEN = "gtceu.structure_preview.open";
    public static final String ENCODE = "gtceu.structure_preview.encode";
    public static final String BUILD = "gtceu.structure_preview.build";
    public static final String PROJECT = "gtceu.structure_preview.project";
    public static final String CANDIDATES = "gtceu.structure_preview.candidates";
    public static final String HIGHLIGHT = "gtceu.structure_preview.highlight";
    public static final String BACK = "gtceu.structure_preview.back";
    public static final String FORWARD = "gtceu.structure_preview.forward";
    public static final String ROOT_ONLY = "gtceu.structure_preview.root_only";
    private static final String VIEW_ID = "structure_preview";
    private static final String HIGHLIGHT_PREFERENCE = "structure_preview.highlight";
    private static final String LEGACY_MINIMAP_PREFERENCE = "structure_preview.minimap";
    private static final int HIGHLIGHT_COLOR = 0x5055FF55;
    private static final int MINIMAP_THRESHOLD = 64;

    private static final int MARGIN = 8;
    private static final int PARTS_PER_ROW = UISizes.SLOTS_PER_ROW;

    public interface Context {

        Layout layout();

        int[] values();

        void closePreview();
    }

    public record Action(String labelKey, Consumer<Context> run) {}

    private final Runnable onBack;
    private final Host host;

    private StructurePreviewScreen(Host host, ModularUI ui, Runnable onBack) {
        super(ui, -1);
        this.host = host;
        this.onBack = onBack;
        ui.initWidgets();
    }

    public static void open(MultiblockMachineDefinition definition, Structure structure, @Nullable Function<Screen, Runnable> back,
                            Action... actions) {
        var minecraft = Minecraft.getInstance();
        minecraft.tell(() -> {
            if (minecraft.player == null) return;
            var previous = minecraft.screen;
            Runnable onBack = back != null ? back.apply(previous) : () -> minecraft.setScreen(previous);
            var window = minecraft.getWindow();
            int width = window.getGuiScaledWidth(), height = window.getGuiScaledHeight();
            var host = new Host(definition, structure, width, height, actions, onBack);
            var ui = new ModularUI(width, height, IUIHolder.EMPTY, minecraft.player).widget(host);
            var screen = new StructurePreviewScreen(host, ui, onBack);
            host.onClose = screen::closePreview;
            minecraft.setScreen(screen);
        });
    }

    @Override
    public void init() {
        modularUI.setSize(width, height);
        host.resize(width, height);
        super.init();
        host.place();
    }

    @Override
    public void removed() {
        super.removed();
        host.minimap.dispose();
        host.scene.releaseGpu();
    }

    @Override
    public void onClose() {
        if (!host.back()) closePreview();
    }

    private void closePreview() {
        if (minecraft != null && minecraft.screen == this) onBack.run();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            host.back();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_5) {
            host.forward();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class Host extends UIElement implements ILayoutHost, ILocalUI {

        private static final String CARD_ID = "structure_preview.config";
        private static final String CANDIDATES_ID = "structure_preview.candidates";

        private final PreviewHistory history;
        private PreviewHistory.Page page;
        private final PreviewPipeline pipeline = new PreviewPipeline();
        private final UIElement heading;
        private final Long2ObjectOpenHashMap<TraceabilityPredicate> cells = new Long2ObjectOpenHashMap<>();
        @Nullable
        private Layout cellsLayout;
        private final UIElement frame;
        private final StructureScene scene;
        private final StructureMinimap minimap;
        private boolean placedMinimap;
        private boolean highlightOn;
        private final Reference2BooleanOpenHashMap<TraceabilityPredicate> replaceable = new Reference2BooleanOpenHashMap<>();
        @Nullable
        private LongArrayList highlightCells;
        private final PartsGrid parts = new PartsGrid(this::toggleShown, PARTS_PER_ROW);
        @Nullable
        private UIElement partsSection;
        @Nullable
        private StructurePlans.Preview preview;
        private StructureConfigView config;
        private final List<Action> actions = new ArrayList<>();
        private final Runnable onBack;
        @Nullable
        private PopupCard card;
        @Nullable
        private PopupCard candidates;
        @Nullable
        private Layout layout;
        @Nullable
        private Long2ObjectOpenHashMap<BlockState> pendingBlocks;
        private PreviewBounds bounds = new PreviewBounds();
        private int layer;
        private int shownLayers;
        private Runnable onClose = () -> {};

        private Host(MultiblockMachineDefinition definition, Structure structure, int width, int height, Action[] actions,
                     Runnable onBack) {
            layout(l -> l.size(width, height));
            for (var action : actions) {
                if (action != null) this.actions.add(action);
            }
            this.onBack = onBack;
            setClientSideWidget();
            page = new PreviewHistory.Page(definition, structure);
            history = new PreviewHistory(page);
            scene = new StructureScene(VIEW_ID, 100, 100, true);
            minimap = new StructureMinimap(scene, UISizes.POPUP_CONTENT_WIDTH + 2 * UISizes.POPUP_PADDING);
            scene.setMinimapAvailable(() -> minimapUseful() && minimap.hasMap());
            scene.setMinimapDefault(Boolean.parseBoolean(UIPreferences.get(LEGACY_MINIMAP_PREFERENCE, "true")));
            highlightOn = Boolean.parseBoolean(UIPreferences.get(HIGHLIGHT_PREFERENCE, "false"));
            heading = UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter().flex(1));
            frame = new UIElement().layout(l -> l.column().paddingAll(UISizes.POPUP_PADDING).gapAll(UISizes.GAP));
            frame.setBackground(UITheme.WINDOW);
            frame.addChild(titleRow());
            scene.setOnSelected((pos, facing) -> showCandidates(pos));
            scene.setWaiting(pipeline::waitProgress);
            config = createConfig();
            fillHeading();
            addChildren(frame, scene, minimap);
            rebuild();
            openCard();
        }

        private UIElement titleRow() {
            var close = Button.glyph("×").setOnClientClick(() -> onClose.run());
            close.setHoverTooltips(MachineWindow.POPUP_CLOSE);
            var toggle = Button.icon(WidgetIcons.SETTINGS).setOnClientClick(this::toggleCard);
            toggle.setSelected(() -> card != null);
            toggle.setHoverTooltips(CONFIG);
            var highlight = Button.icon(WidgetIcons.HIGHLIGHT).setOnClientClick(this::toggleHighlight)
                    .setVariant(() -> highlightOn ? UITheme.ButtonVariant.CONFIRM : UITheme.ButtonVariant.DEFAULT);
            highlight.setHoverTooltips(HIGHLIGHT);
            var tools = ZoomBar.of(scene, false).fit(ZoomBar.RESET).minimap().add(highlight).build();
            return UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter())
                    .addChildren(heading, tools, toggle, close);
        }

        private StructureConfigView createConfig() {
            return new StructureConfigView(page.definition, page.structure, page.values, page.excluded, () -> {
                pipeline.scheduleRebuild();
                scene.cancelRender();
            }, this::navigate, UISizes.POPUP_CONTENT_WIDTH, frameWidth() / 2, frameHeight() / 2);
        }

        private void fillHeading() {
            heading.clearAllWidgets();
            if (history.hasMany()) {
                var back = Button.icon(UITheme.ARROW_LEFT).setOnClientClick(this::back).disabled(history::isRoot, null);
                back.setHoverTooltips(BACK);
                var forward = Button.icon(UITheme.ARROW_RIGHT).setOnClientClick(this::forward).disabled(() -> !history.canForward(), null);
                forward.setHoverTooltips(FORWARD);
                heading.addChildren(back, forward);
            }
            var icon = page.definition.asStack();
            heading.addChildren(ItemView.of(icon),
                    TextLine.constant(LayoutStyle.AUTO, Component.translatable(TITLE, icon.getHoverName())).layout(l -> l.flex(1)));
            UIElement.markLayoutDirty(heading);
        }

        private void navigate(MultiblockMachineDefinition definition) {
            var structure = StructurePattern.of(definition);
            if (structure == null) return;
            show(history.push(new PreviewHistory.Page(definition, structure)));
        }

        private boolean back() {
            if (history.isRoot()) return false;
            show(history.index() - 1);
            return true;
        }

        private void forward() {
            if (history.canForward()) show(history.index() + 1);
        }

        private void show(int target) {
            page.camera = scene.camera();
            page.layer = layer;
            page = history.select(target);
            layer = page.layer;
            pipeline.cancel();
            scene.cancelRender();
            scene.recenter(page.camera);
            config = createConfig();
            fillHeading();
            boolean open = card != null;
            closeCard();
            rebuild();
            if (open) openCard();
        }

        private void resize(int width, int height) {
            layout(l -> l.size(width, height));
        }

        private int frameWidth() {
            return getSizeWidth() - 2 * MARGIN;
        }

        private int frameHeight() {
            return getSizeHeight() - 2 * MARGIN;
        }

        private void place() {
            int width = frameWidth(), height = frameHeight();
            frame.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(MARGIN).top(MARGIN).size(width, height));
            int top = MARGIN + UISizes.POPUP_PADDING + UISizes.CONTROL_HEIGHT + UISizes.GAP;
            int left = MARGIN + UISizes.POPUP_PADDING;
            int right = MARGIN + width - UISizes.POPUP_PADDING;
            int inset = getSizeWidth() - right + UISizes.GAP;
            scene.setPreferredSize(right - left, MARGIN + height - UISizes.POPUP_PADDING - top);
            scene.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(left).top(top));
            int cardTop = top + UISizes.GAP;
            placedMinimap = minimapShown();
            minimap.setDisplay(placedMinimap);
            int minimapTop = cardTop;
            minimap.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).right(inset).top(minimapTop));
            if (placedMinimap) cardTop += minimap.getSizeHeight() + UISizes.GAP;
            int cardHeight = MARGIN + height - UISizes.POPUP_PADDING - cardTop - UISizes.GAP;
            int finalCardTop = cardTop;
            if (card != null) {
                card.setMaxHeight(cardHeight);
                card.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).right(inset).top(finalCardTop));
            }
            if (candidates != null) {
                candidates.setMaxHeight(cardHeight);
                candidates.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(left + UISizes.GAP).top(top + UISizes.GAP));
            }
        }

        @Override
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if (placedMinimap != minimapShown()) place();
            long now = System.currentTimeMillis();
            if (pipeline.modelChanged(chosenModel()) && layout != null) pipeline.scheduleRebuild();
            if (pipeline.rebuildDue(now)) {
                scene.continueProgress();
                rebuild();
            } else if (pipeline.layerDue(now)) {
                scene.continueProgress();
                showBlocks();
            }
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        }

        @Nullable
        private PatternBuilderModel chosenModel() {
            return config.listLayout() == null ? null : StructureBuildFlow.chosen(page.definition, page.values, page.excluded);
        }

        private boolean minimapUseful() {
            return layout != null && (layout.width() > MINIMAP_THRESHOLD || layout.depth() > MINIMAP_THRESHOLD);
        }

        private boolean minimapShown() {
            return scene.isMinimapShown() && scene.isMinimapAvailable();
        }

        private void toggleHighlight() {
            highlightOn = !highlightOn;
            UIPreferences.put(HIGHLIGHT_PREFERENCE, String.valueOf(highlightOn));
            applyHighlight();
        }

        private void applyHighlight() {
            if (!highlightOn || layout == null) {
                scene.setHighlight(null, HIGHLIGHT_COLOR);
                return;
            }
            if (highlightCells == null) {
                highlightCells = new LongArrayList();
                for (var cell : layout.cells()) {
                    if (isReplaceable(cell.predicate())) highlightCells.add(BlockPos.asLong(cell.x(), cell.y(), cell.z()));
                }
            }
            scene.setHighlight(highlightCells, HIGHLIGHT_COLOR);
        }

        private boolean isReplaceable(TraceabilityPredicate predicate) {
            if (replaceable.containsKey(predicate)) return replaceable.getBoolean(predicate);
            boolean[] part = { false };
            predicate.forEachSimple(simple -> {
                if (simple == null || part[0]) return;
                for (var stack : simple.getCandidates()) {
                    if (stack.getItem() instanceof MetaMachineItem machine && machine.getDefinition() != page.definition) {
                        part[0] = true;
                        return;
                    }
                }
            });
            boolean result = part[0];
            replaceable.put(predicate, result);
            return result;
        }

        private void toggleCard() {
            if (card != null) closeCard();
            else openCard();
        }

        private void openCard() {
            if (card != null) return;
            shownLayers = layerCount();
            var popup = Popup.of(() -> Component.translatable(CONFIG), column -> {
                column.addChild(config);
                var layerRow = UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter()).addChildren(
                        TextLine.translatable(LayoutStyle.AUTO, LAYER).setColor(UITheme.PANEL_TEXT).layout(l -> l.flex(1)),
                        new Stepper(UISizes.VALUE_WIDTH * 2, () -> layer, this::setLayer, 0, shownLayers, true,
                                value -> value == 0 ? I18n.get(LAYER_ALL) : I18n.get(LAYER_N, value)));
                column.addChild(UIElement.section().addChild(layerRow));
                partsSection = UIElement.section();
                partsSection.addChild(TextLine.translatable(LayoutStyle.AUTO, PARTS).setColor(UITheme.PANEL_TEXT));
                partsSection.addChild(parts);
                column.addChild(partsSection);
                if (!actions.isEmpty()) column.addChild(actionRow());
            });
            card = new PopupCard(CARD_ID, popup, Math.max(UISizes.SLOT, frameHeight()), this::closeCard);
            place();
            addChild(card);
        }

        private UIElement actionRow() {
            var row = UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter());
            row.disabled(() -> !history.isRoot(), ROOT_ONLY);
            boolean single = actions.size() == 1;
            if (single) row.addChild(UIElement.flexSpacer());
            for (int i = 0; i < actions.size(); i++) {
                var action = actions.get(i);
                var button = single ? Button.translatable(UISizes.BUTTON_WIDTH * 2, action.labelKey()) :
                        Button.translatable(LayoutStyle.AUTO, action.labelKey()).layout(l -> l.flex(1));
                row.addChild(button
                        .setVariant(i == actions.size() - 1 ? UITheme.ButtonVariant.CONFIRM : UITheme.ButtonVariant.DEFAULT)
                        .disabled(() -> config.currentLayout() == null, StructureConfigView.INVALID)
                        .setOnClientClick(() -> runAction(action)));
            }
            return row;
        }

        private void runAction(Action action) {
            var current = config.listLayout();
            if (current == null) return;
            action.run().accept(new Context() {

                @Override
                public Layout layout() {
                    return current;
                }

                @Override
                public int[] values() {
                    return page.values.clone();
                }

                @Override
                public void closePreview() {
                    onBack.run();
                }
            });
        }

        private void closeCard() {
            if (card == null) return;
            removeWidget(card);
            card = null;
            place();
        }

        private void showCandidates(BlockPos pos) {
            var predicate = cells.get(pos.asLong());
            closeCandidates();
            if (predicate == null) return;
            var popup = Popup.of(() -> Component.translatable(CANDIDATES),
                    column -> predicate.forEachSimple(simple -> addCandidates(column, predicate, simple)));
            candidates = new PopupCard(CANDIDATES_ID, popup, Math.max(UISizes.SLOT, frameHeight()), this::closeCandidates);
            place();
            addChild(candidates);
        }

        private static void addCandidates(UIElement column, TraceabilityPredicate predicate, @Nullable SimplePredicate simple) {
            if (simple == null) return;
            var stacks = simple.getCandidates();
            if (stacks.isEmpty()) return;
            var tips = simple.getToolTips(predicate);
            var section = UIElement.section();
            for (var tip : tips) section.addChild(TextLine.constant(LayoutStyle.AUTO, tip).setColor(UITheme.PANEL_TEXT));
            section.addChild(PartSlots.grid(stacks, tips, PARTS_PER_ROW));
            column.addChild(section);
        }

        private void closeCandidates() {
            if (candidates == null) return;
            removeWidget(candidates);
            candidates = null;
            place();
        }

        private void setLayer(int value) {
            layer = value;
            pipeline.scheduleLayer();
            scene.cancelRender();
        }

        private int layerCount() {
            return bounds.empty ? 0 : bounds.height();
        }

        private void rebuild() {
            layout = config.currentLayout();
            if (layout == null) return;
            var listed = config.listLayout();
            var model = listed == null ? null : StructureBuildFlow.chosen(page.definition, page.values, page.excluded);
            var preview = pipeline.build(page.definition, layout, listed, model);
            this.preview = preview;
            if (cellsLayout != layout) {
                cellsLayout = layout;
                cells.clear();
                for (var cell : layout.cells()) cells.put(BlockPos.asLong(cell.x(), cell.y(), cell.z()), cell.predicate());
                highlightCells = null;
                replaceable.clear();
            }
            applyHighlight();
            closeCandidates();
            var blocks = preview.blocks();
            bounds = PreviewBounds.of(blocks);
            minimap.setBlocks(blocks, bounds);
            int layers = layerCount();
            if (layer > layers) layer = 0;
            var present = new ReferenceOpenHashSet<Item>();
            for (var stack : preview.parts()) present.add(stack.getItem());
            page.shown.retainAll(present);
            pendingBlocks = page.shown.isEmpty() ? blocks : preview.blocks(page.shown::contains);
            showBlocks();
            fillParts(preview.parts());
            if (card != null && layers != shownLayers) {
                closeCard();
                openCard();
            }
            place();
        }

        private void showBlocks() {
            float zoom = layout == null ? -1 : StructureScene.fitZoom(layout.width(), layout.height(), layout.depth());
            scene.show(pendingBlocks, layer == 0 ? StructureScene.ALL_LAYERS : bounds.minY + layer - 1, zoom);
            pendingBlocks = null;
        }

        private void fillParts(List<ItemStack> stacks) {
            parts.fill(stacks, PartSlots.create(stacks, Component.translatable(PARTS_FILTER)), page.shown, PARTS_PER_ROW);
        }

        private void fitParts() {
            if (partsSection == null || card == null) return;
            int columns = Math.max(PARTS_PER_ROW, (partsSection.getSizeWidth() - 2 * UITheme.PANEL_PADDING) / UISizes.SLOT);
            parts.arrange(columns);
        }

        private void toggleShown(Item item) {
            if (!page.shown.remove(item)) page.shown.add(item);
            if (preview == null) return;
            pendingBlocks = page.shown.isEmpty() ? preview.blocks() : preview.blocks(page.shown::contains);
            scene.cancelRender();
            showBlocks();
        }

        @Override
        public void onContentResized(Widget root) {}

        @Override
        protected void onLayoutFinished() {
            fitParts();
        }
    }
}
