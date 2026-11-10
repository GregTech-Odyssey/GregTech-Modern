package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureDiagnosis;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureDiagnosis.Row;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldData;
import com.gregtechceu.gtceu.common.network.RequestThrottle;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.Label;
import com.gregtechceu.gtceu.uipro.elements.ProgressBar;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.display.DetailsTab;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.gto.datasynclib.datastream.codec.StreamCodec;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 多方块控制器的"结构诊断"页签：打开即在后台跑全量诊断（结构变化时自动重跑），逐组列出全部结构问题，并可在世界中标出位置。
 */
public final class StructureDiagnosisTab implements IFancyUIProvider {

    public static final Component TITLE = Component.translatable("gtceu.structure.diagnosis.title");
    private static final Component STRUCTURE = Component.translatable("gtceu.structure.diagnosis.structure");
    private static final Component FORMED = Component.translatable("gtceu.structure.diagnosis.formed");
    private static final Component UNFORMED = Component.translatable("gtceu.structure.diagnosis.unformed");
    private static final Component RUNNING = Component.translatable("gtceu.structure.diagnosis.running");
    private static final Component PROGRESS = Component.translatable("gtceu.structure.diagnosis.progress");
    private static final Component BASIS = Component.translatable("gtceu.structure.diagnosis.basis");
    private static final Component BASIS_DETAIL = Component.translatable("gtceu.structure.diagnosis.basis.detail");
    private static final Component DASH = Component.literal("—");
    private static final Component FOOTER = Component.translatable("gtceu.structure.diagnosis.footer.full");
    private static final Component RESULT = Component.translatable("gtceu.structure.diagnosis.result");
    private static final Component RESULT_NONE = Component.translatable("gtceu.structure.diagnosis.result.none");
    private static final Component RESULT_CLEAN = Component.translatable("gtceu.structure.diagnosis.result.clean");
    private static final Component SHOW_ALL = Component.translatable("gtceu.structure.diagnosis.show_all");
    private static final Component SHOW_ALL_TIP = Component.translatable("gtceu.structure.diagnosis.show_all.tip");
    private static final Component SHOW_NONE = Component.translatable("gtceu.structure.diagnosis.show_all.none");
    private static final Component SHOW_CLEAR = Component.translatable("gtceu.structure.diagnosis.show_all.clear");
    private static final String SHOWN = "gtceu.structure.diagnosis.shown";
    private static final String SHOWN_PARTIAL = "gtceu.structure.diagnosis.shown_partial";
    private static final Component TIP = Component.translatable("gtceu.structure.diagnosis.tip");
    private static final Component WAIT_CHECK = Component.translatable("gtceu.structure.diagnosis.wait_check");
    private static final Component LEGEND = Component.translatable("gtceu.structure.diagnosis.list");
    private static final List<Row> CLEAN_ROWS = List.of(new Row(StructureDiagnosis.WARNING, 0, RESULT_CLEAN, Component.empty(), null, Items.AIR,
            List.of(new StructureDiagnosis.Line(TIP, WAIT_CHECK, Component.empty())), new long[0]));
    private static final int TRAY_BORDER = 1;
    private static final int FIXED_HEIGHT = UISizes.CONTROL_HEIGHT + UISizes.SECTION_GAP + StatusPanel.heightFor(3) + UISizes.GAP + ProgressBar.HEIGHT +
            UISizes.SECTION_GAP + Label.LINE_HEIGHT + 3 * UISizes.GAP + Label.LINE_HEIGHT;
    private static final long[] NO_POSITIONS = new long[0];
    private static final StreamCodec<FriendlyByteBuf, long[]> POSITIONS_CODEC = new StreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, long[] value) {
            buf.writeVarInt(value.length);
            for (long pos : value) buf.writeLong(pos);
        }

        @Override
        public long[] decode(FriendlyByteBuf buf) {
            var positions = new long[Math.min(buf.readVarInt(), StructureDiagnosis.MAX_POSITIONS)];
            for (int i = 0; i < positions.length; i++) positions[i] = buf.readLong();
            return positions;
        }
    };

    private final IMultiController controller;
    @Nullable
    private volatile Full full;
    private volatile boolean running;
    private volatile int progress;
    private ProgressBar.Progress shownProgress = new ProgressBar.Progress(0, StructureDiagnosis.PROGRESS_SCALE, 0);
    @Nullable
    private Object fingerprintIssue;
    private long fingerprint;
    @Nullable
    private List<Row> positionsSource;
    private long[] positions = NO_POSITIONS;
    private int locatedTotal;
    @Nullable
    private Object formedAssembly;
    private Component formedSize = DASH;

    private record Full(StructureDiagnosis.Report report, long fingerprint, Component result, Level level) {}

    private StructureDiagnosisTab(IMultiController controller) {
        this.controller = controller;
    }

    public static void attach(TabsWidget tabs, IFancyUIProvider home) {
        if (!(home instanceof IMultiController controller) || !(controller.self().getDefinition() instanceof MultiblockMachineDefinition definition) ||
                !definition.hasStructure()) {
            return;
        }
        var subTabs = tabs.getSubTabs();
        int index = 0;
        for (int i = 0; i < subTabs.size(); i++) {
            if (subTabs.get(i) instanceof DetailsTab) {
                index = i + 1;
                break;
            }
        }
        tabs.attachSubTab(index, new StructureDiagnosisTab(controller));
    }

    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        if (!controller.self().isRemote()) full = null;
        var content = new Page();
        content.layout(l -> l.column().minWidth(UISizes.CONTENT_WIDTH).gapAll(UISizes.SECTION_GAP));
        var located = content.addSyncValue(SyncValue.ofInt(this::locatedTotal, 0));
        var shown = content.addSyncValue(SyncValue.of(this::positions, POSITIONS_CODEC, NO_POSITIONS).onChanged(value -> {
            if (controller.self().isRemote()) clientShowNew(value, located.getValue());
        }));

        var show = Button.of(LayoutStyle.AUTO).bindClientText(() -> (controller.self().isRemote() && clientShowing(shown.getValue()) ? SHOW_CLEAR : SHOW_ALL).getString())
                .tooltips(SHOW_ALL_TIP);
        show.layout(l -> l.flex(1));
        show.clientDisabled(() -> shown.getValue().length == 0, SHOW_NONE);
        show.setOnClientClick(() -> {
            if (controller.self().isRemote()) clientToggle(shown.getValue(), located.getValue());
        });
        content.addChild(UIElement.centeredRow(UISizes.CONTROL_HEIGHT).addChild(show));

        var summary = new StatusPanel();
        summary.addLine(STRUCTURE, () -> controller.isFormed() ? FORMED : UNFORMED).bindLevel(() -> controller.isFormed() ? Level.GOOD : Level.ERROR);
        summary.addLine(BASIS, this::basisText).bindDetail(() -> BASIS_DETAIL);
        summary.addLine(RESULT, this::resultText).bindLevel(this::resultLevel);
        var bar = ProgressBar.of(LayoutStyle.AUTO, PROGRESS, 0, this::progress).bindClientColor(UITheme::barProgress).percent();
        bar.layout(l -> l.marginTop(UISizes.GAP));
        bar.setDisplay(false);
        summary.addChild(bar);
        content.addSyncValue(SyncValue.ofBool(this::isBusy, false).onChanged(busy -> bar.setDisplay(busy)));
        content.addChild(summary);

        var group = UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP));
        var rule = new UIElement().layout(l -> l.flex(1).height(Label.LINE_HEIGHT));
        rule.setBackground(UITheme.ETCH);
        group.addChild(UIElement.row(Label.LINE_HEIGHT).layout(l -> l.gapAll(UISizes.TEXT_PADDING).alignCenter())
                .addChild(Label.constant(UISizes.CONTENT_WIDTH, LEGEND)).addChild(rule));
        var list = StructureIssueList.of(LayoutStyle.AUTO, this::rows, controller.self().getPos(), row -> {
            if (controller.self().isRemote()) clientShow(row.positions(), row.count());
        });
        var tray = ScrollerView.page("structure_diagnosis.list", UISizes.CONTENT_WIDTH).reservePageHeight(FIXED_HEIGHT);
        tray.setBackground(UITheme.TRAY);
        tray.setViewportInset(TRAY_BORDER).addScrollViewChild(UIElement.column(LayoutStyle.AUTO).layout(l -> l.paddingAll(UISizes.GAP)).addChild(list));
        group.addChild(tray);
        var footer = Label.constant(UISizes.CONTENT_WIDTH, FOOTER).bindClientColor(UITheme::textSecondary);
        footer.layout(l -> l.marginTop(UISizes.GAP));
        group.addChild(footer);
        group.setDisplay(false);
        footer.setDisplay(false);
        content.addSyncValue(SyncValue.ofInt(this::listState, 0).onChanged(state -> {
            group.setDisplay(state > 0);
            footer.setDisplay(state > 1);
        }));
        content.addChild(group);
        return content;
    }

    private final class Page extends UIElement {

        @Override
        public void detectAndSendChanges() {
            var gui = getGui();
            serverTick(gui == null ? null : gui.entityPlayer);
            super.detectAndSendChanges();
        }
    }

    @Nullable
    private Full currentFull() {
        var current = full;
        return current == null || controller.isFormed() ? null : current;
    }

    private boolean isBusy() {
        if (controller.isFormed()) return false;
        var current = full;
        return running || current == null || current.fingerprint != fingerprint();
    }

    private void serverTick(@Nullable Player player) {
        if (running || !isBusy() || !(player instanceof ServerPlayer serverPlayer)) return;
        var machine = controller.self();
        if (machine.isRemoved() || machine.getLevel() != serverPlayer.level()) return;
        if (!RequestThrottle.STRUCTURE_DIAGNOSIS.tryAcquire(serverPlayer)) return;
        var request = StructureDiagnosis.request(controller);
        if (request == null) return;
        long started = fingerprint();
        progress = 0;
        running = true;
        MultiblockWorldData.execute(() -> {
            try {
                StructureDiagnosis.Report report;
                try {
                    report = StructureDiagnosis.run(request, value -> progress = value);
                } catch (Throwable error) {
                    GTCEu.LOGGER.error("Structure diagnosis of {} failed", request.center(), error);
                    report = new StructureDiagnosis.Report(List.of(), 0, 0, StructureDiagnosis.NONE, StructureDiagnosis.FAILED);
                }
                full = complete(report, started);
            } finally {
                running = false;
            }
        });
    }

    private long fingerprint() {
        var issue = controller.getMultiblockState().getReportedIssue();
        if (issue != fingerprintIssue) {
            fingerprintIssue = issue;
            fingerprint = issue == null ? 0 : issue.fingerprint();
        }
        return fingerprint;
    }

    private ProgressBar.Progress progress() {
        int value = progress;
        if (shownProgress.current() != value) shownProgress = new ProgressBar.Progress(value, StructureDiagnosis.PROGRESS_SCALE, 0);
        return shownProgress;
    }

    private List<Row> rows() {
        var current = currentFull();
        if (current == null) return List.of();
        return current.report.rows().isEmpty() && current.report.note() == null ? CLEAN_ROWS : current.report.rows();
    }

    private long[] positions() {
        var rows = rows();
        if (rows != positionsSource) {
            positionsSource = rows;
            var union = new LongArrayList();
            int total = 0;
            for (var row : rows) {
                if (row.positions().length > 0) total += row.count();
                for (long pos : row.positions()) {
                    if (union.size() < StructureDiagnosis.MAX_POSITIONS) union.add(pos);
                }
            }
            positions = union.isEmpty() ? NO_POSITIONS : union.toLongArray();
            locatedTotal = total;
        }
        return positions;
    }

    private Integer locatedTotal() {
        positions();
        return locatedTotal;
    }

    private Component basisText() {
        if (controller.isFormed()) {
            var assembly = controller.getAssembly();
            if (assembly != formedAssembly) {
                formedAssembly = assembly;
                formedSize = StructureDiagnosis.describeFormed(controller);
            }
            return formedSize;
        }
        var current = currentFull();
        return current == null ? DASH : current.report.basis();
    }

    private Integer listState() {
        if (rows().isEmpty()) return 0;
        return positions().length > 0 ? 2 : 1;
    }

    private Component resultText() {
        if (controller.isFormed()) return RESULT_NONE;
        var current = currentFull();
        return current == null || running ? RUNNING : current.result;
    }

    private Level resultLevel() {
        var current = controller.isFormed() || running ? null : currentFull();
        return current == null ? Level.NORMAL : current.level;
    }

    private static Full complete(StructureDiagnosis.Report report, long fingerprint) {
        if (report.note() != null) return new Full(report, fingerprint, report.note(), Level.WARNING);
        if (report.rows().isEmpty()) return new Full(report, fingerprint, RESULT_CLEAN, Level.WARNING);
        var result = Component.translatable("gtceu.structure.diagnosis.result.count", report.groups());
        return new Full(report, fingerprint, result, Level.NORMAL);
    }

    @OnlyIn(Dist.CLIENT)
    private void clientShowNew(long[] value, int total) {
        if (value.length > 0) clientShow(value, Math.max(total, value.length));
    }

    @OnlyIn(Dist.CLIENT)
    private void clientShow(long[] cells, int total) {
        StructureIssueOverlay.showAll(controller.self().getPos(), cells);
        var player = Minecraft.getInstance().player;
        if (player == null || cells.length == 0) return;
        var message = total > cells.length ? Component.translatable(SHOWN_PARTIAL, cells.length, total) : Component.translatable(SHOWN, cells.length);
        player.displayClientMessage(message, true);
    }

    @OnlyIn(Dist.CLIENT)
    private boolean clientShowing(long[] cells) {
        return cells.length > 0 && StructureIssueOverlay.isShowing(controller.self().getPos(), cells);
    }

    @OnlyIn(Dist.CLIENT)
    private void clientToggle(long[] cells, int total) {
        if (clientShowing(cells)) StructureIssueOverlay.clear();
        else clientShow(cells, Math.max(total, cells.length));
    }

    @Override
    public IGuiTexture getTabIcon() {
        return WidgetIcons.DIAGNOSIS;
    }

    @Override
    public Component getTitle() {
        return TITLE;
    }

    @Override
    public List<Component> getTabTooltips() {
        return List.of(TITLE);
    }

    @Override
    public boolean hasPlayerInventory() {
        return false;
    }
}
