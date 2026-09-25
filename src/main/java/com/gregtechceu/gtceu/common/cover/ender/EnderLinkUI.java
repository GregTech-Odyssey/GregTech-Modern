package com.gregtechceu.gtceu.common.cover.ender;

import com.gregtechceu.gtceu.api.machine.MachineCoverContainer;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEnderRegistry;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.elements.TextField;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class EnderLinkUI {

    static final Component NO_VALUE = Component.literal("—");
    private static final int COLOR_LENGTH = 8;
    private static final int DESCRIPTION_MAX_LENGTH = 64;
    private static final int MAX_CHANNELS = 256;
    private static final int LIST_MAX_ROWS = 5;
    private static final int ROW_HEIGHT = 3 * UISizes.SMALL_TEXT_HEIGHT;
    private static final int LIST_WIDTH = UISizes.CONTENT_WIDTH - 2 * UITheme.PANEL_PADDING;
    private static final int ACCESS_CHECK_TICKS = 20;

    private EnderLinkUI() {}

    static Widget create(AbstractEnderLinkCover<?> cover) {
        var ctx = new Context(cover);
        var main = mainPage(ctx);
        var list = listPage(ctx);
        list.setDisplay(false);
        var root = CoverUIs.page();
        root.addSyncValue(SyncValue.of(ctx::listShown, SyncValue.BOOLEAN, false).onChanged(shown -> {
            main.setDisplay(!shown);
            list.setDisplay(shown);
        }));
        ctx.root = root;
        return root.addChildren(main, list);
    }

    private static UIElement mainPage(Context ctx) {
        var cover = ctx.cover;
        var page = CoverUIs.page();
        page.addChild(channelSection(ctx));
        var status = new StatusPanel();
        cover.addEntryStatus(status, ctx::canAccess);
        page.addChild(status);
        page.addChild(transferSection(cover));
        var filter = cover.getFilterHandler();
        if (filter != null) page.addChild(CoverUIs.filterSection(filter));
        return page;
    }

    private static UIElement channelSection(Context ctx) {
        var cover = ctx.cover;
        var section = CoverUIs.section("cover.ender_link.ui.channel");
        section.disabled(() -> !ctx.canAccess(), "cover.ender_link.ui.private_no_access");

        section.addChild(CoverUIs.enumRow("cover.ender_link.ui.permission", List.of(AbstractEnderLinkCover.Permissions.values()),
                cover::getPermission, cover::setPermission));

        var name = new TextField(0, () -> cover.colorStr, ctx::submitChannelName);
        name.layout(l -> l.flex(1));
        name.commitOnSubmit(text -> AbstractEnderLinkCover.COLOR_INPUT_PATTERN.matcher(text).matches());
        name.getInput().setMaxStringLength(COLOR_LENGTH);
        name.setHoverTooltips(Component.translatable("cover.ender_link.ui.channel_name.tooltip"));
        section.addChild(UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter())
                .addChildren(EnderColorBlock.of(cover::getColor), name));

        var description = new TextField(0, ctx::currentDescription, ctx::setCurrentDescription);
        description.layout(l -> l.flex(1));
        description.setPlaceholder(() -> Component.translatable("cover.ender_link.ui.description.placeholder"));
        description.getInput().setMaxStringLength(DESCRIPTION_MAX_LENGTH);
        description.setHoverTooltips(Component.translatable("cover.ender_link.ui.description.tooltip"));
        section.addChild(UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.alignCenter()).addChild(description));

        section.addChild(Button.translatable(LayoutStyle.AUTO, "cover.ender_link.ui.open_list").setOnServerClick(ctx::openList));
        return section;
    }

    private static UIElement transferSection(AbstractEnderLinkCover<?> cover) {
        var section = CoverUIs.section("cover.ui.transfer").addChildren(
                CoverUIs.controlRow("cover.ender_link.ui.working", Switch.of(cover::isWorkingEnabled, cover::setWorkingEnabled)),
                CoverUIs.enumRow("cover.ui.io", List.of(IO.IN, IO.OUT), cover::getIo, cover::setIo, cover.ioTooltipKey()));
        if (cover.hasManualIO()) {
            section.addChild(CoverUIs.enumRow("cover.ui.manual_io", List.of(ManualIOMode.VALUES),
                    cover::getManualIOMode, cover::setManualIOMode,
                    "cover.universal.manual_import_export.mode.description.0",
                    "cover.universal.manual_import_export.mode.description.1",
                    "cover.universal.manual_import_export.mode.description.2"));
        }
        return section;
    }

    private static UIElement listPage(Context ctx) {
        var cover = ctx.cover;
        var page = CoverUIs.page();
        var back = Button.icon(UITheme.ARROW_LEFT).setOnServerClick(ctx::closeList);
        back.setHoverTooltips("cover.ender_link.ui.back");
        var title = TextLine.of(0, () -> Component.translatable(cover.getPermission() == AbstractEnderLinkCover.Permissions.PRIVATE ?
                "cover.ender_link.ui.list.private" : "cover.ender_link.ui.list.public"));
        title.layout(l -> l.flex(1));
        page.addChild(UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter()).addChildren(back, title));

        var scroller = new ScrollerView("ender_link.channels", LIST_WIDTH, listHeight(1)).adaptiveHeight(listHeight(LIST_MAX_ROWS));
        scroller.addScrollViewChild(new EnderChannelRows(ctx.remote, ctx::listNames, ctx::revision,
                key -> channelRow(ctx, key), Component.translatable("cover.ender_link.ui.list.empty")));
        page.addChild(UIElement.section().addChild(scroller));
        return page;
    }

    private static int listHeight(int rows) {
        return rows * ROW_HEIGHT + (rows - 1) * UISizes.GAP;
    }

    private static UIElement channelRow(Context ctx, String key) {
        var cover = ctx.cover;
        var row = UIElement.row(ROW_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter());
        var current = row.addSyncValue(SyncValue.of(() -> ctx.isCurrent(key), SyncValue.BOOLEAN, false));

        var colorText = key.substring(Math.min(key.length(), cover.identifier().length()));
        var name = TextLine.constant(LayoutStyle.AUTO, Component.literal(colorText)).setSmall().setColor(UITheme.PANEL_TEXT);
        var description = TextLine.of(LayoutStyle.AUTO, () -> ctx.descriptionOf(key)).setSmall().setColor(UITheme.TEXT_SECONDARY);
        var summary = TextLine.of(LayoutStyle.AUTO, () -> ctx.summaryOf(key)).setSmall().setColor(UITheme.TEXT_SECONDARY);
        var info = new UIElement().layout(l -> l.column().flex(1)).addChildren(name, description, summary);

        var select = Button.text(UISizes.BUTTON_WIDTH, () -> Component.translatable(current.getValue() ?
                "cover.ender_link.ui.current" : "cover.ender_link.ui.select").getString())
                .disabled(() -> ctx.isCurrent(key), "cover.ender_link.ui.already_current")
                .setOnServerClick(() -> ctx.select(key));
        var clear = Button.glyph("×")
                .disabled(() -> !ctx.hasDescription(key), "cover.ender_link.ui.no_description")
                .setOnServerClick(() -> ctx.clearDescription(key));
        clear.setHoverTooltips("cover.ender_link.ui.clear_description");

        return row.addChildren(EnderColorBlock.constant(parseColor(colorText)), info, select, clear);
    }

    private static int parseColor(String colorText) {
        if (colorText.length() != COLOR_LENGTH || !AbstractEnderLinkCover.COLOR_INPUT_PATTERN.matcher(colorText).matches()) return -1;
        return VirtualEntry.parseColor(colorText);
    }

    private static final class Context {

        final AbstractEnderLinkCover<?> cover;
        final boolean remote;
        @Nullable
        Widget root;
        private boolean showList;
        private int revision;
        @Nullable
        private UUID checkedOwner;
        private long checkedBucket = Long.MIN_VALUE;
        private boolean access;

        Context(AbstractEnderLinkCover<?> cover) {
            this.cover = cover;
            this.remote = cover.isRemote();
        }

        @Nullable
        Player player() {
            return root == null || root.getGui() == null ? null : root.getGui().entityPlayer;
        }

        int revision() {
            return revision;
        }

        boolean canAccess() {
            if (remote) return true;
            var owner = cover.getOwner();
            if (owner == null) return true;
            var player = player();
            if (player == null) return false;
            var level = cover.coverHolder.getLevel();
            long bucket = level == null ? 0 : level.getGameTime() / ACCESS_CHECK_TICKS;
            if (!owner.equals(checkedOwner) || bucket != checkedBucket) {
                checkedOwner = owner;
                checkedBucket = bucket;
                access = computeAccess(player, owner);
            }
            return access;
        }

        private boolean computeAccess(Player player, UUID owner) {
            if (player.hasPermissions(ConfigHolder.INSTANCE.machines.ownerOPBypass)) return true;
            if (!(cover.coverHolder instanceof MachineCoverContainer)) return true;
            var machineOwner = MachineOwner.getOwner(owner);
            return machineOwner == null || machineOwner.isPlayerInTeam(player) || machineOwner.isPlayerFriendly(player);
        }

        boolean listShown() {
            return showList && canAccess();
        }

        void openList() {
            if (!canAccess()) return;
            showList = true;
            revision++;
        }

        void closeList() {
            showList = false;
        }

        List<String> listNames() {
            if (!listShown()) return List.of();
            var prefix = cover.identifier();
            return VirtualEnderRegistry.getInstance().getEntryNames(cover.getOwner(), cover.getEntryType()).stream()
                    .filter(name -> name.startsWith(prefix))
                    .sorted(Comparator.comparing(name -> name.substring(prefix.length()).toUpperCase(Locale.ROOT)))
                    .limit(MAX_CHANNELS)
                    .toList();
        }

        @Nullable
        private VirtualEntry entry(String key) {
            if (!canAccess()) return null;
            return VirtualEnderRegistry.getInstance().getEntry(cover.getOwner(), cover.getEntryType(), key);
        }

        boolean isCurrent(String key) {
            return key.equals(cover.getChannelName());
        }

        boolean hasDescription(String key) {
            var entry = entry(key);
            return entry != null && !entry.getDescription().isEmpty();
        }

        Component descriptionOf(String key) {
            var entry = entry(key);
            if (entry == null) return Component.empty();
            var description = entry.getDescription();
            return description.isEmpty() ? Component.translatable("cover.ender_link.ui.no_description") : Component.literal(description);
        }

        Component summaryOf(String key) {
            var entry = entry(key);
            return entry == null ? Component.empty() : cover.summaryOf(entry);
        }

        void select(String key) {
            if (!canAccess() || isCurrent(key) || entry(key) == null) return;
            var prefix = cover.identifier();
            if (!key.startsWith(prefix)) return;
            var color = key.substring(prefix.length());
            if (color.length() != COLOR_LENGTH || !AbstractEnderLinkCover.COLOR_INPUT_PATTERN.matcher(color).matches()) return;
            cover.setChannelName(color);
            revision++;
        }

        void clearDescription(String key) {
            var entry = entry(key);
            if (entry == null) return;
            entry.setDescription("");
            revision++;
        }

        void submitChannelName(String text) {
            if (!canAccess() || text == null || text.isEmpty() || text.length() > COLOR_LENGTH ||
                    !AbstractEnderLinkCover.COLOR_INPUT_PATTERN.matcher(text).matches())
                return;
            var color = (text + "F".repeat(COLOR_LENGTH - text.length())).toUpperCase(Locale.ROOT);
            if (color.equals(cover.colorStr)) return;
            cover.setChannelName(color);
            revision++;
        }

        String currentDescription() {
            return canAccess() ? cover.getEntry().getDescription() : "";
        }

        void setCurrentDescription(String text) {
            if (!canAccess() || text == null) return;
            cover.getEntry().setDescription(text.length() > DESCRIPTION_MAX_LENGTH ? text.substring(0, DESCRIPTION_MAX_LENGTH) : text);
        }
    }
}
