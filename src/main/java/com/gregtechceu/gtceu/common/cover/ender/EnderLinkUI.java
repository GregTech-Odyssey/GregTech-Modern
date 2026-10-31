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
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.ServerList;
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

import com.gto.datasynclib.datastream.codec.ByteBufCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class EnderLinkUI {

    static final Component NO_VALUE = Component.literal("—");
    private static final Component SECTION_CHANNEL = Component.translatable("cover.ender_link.ui.channel");
    private static final Component PRIVATE_NO_ACCESS = Component.translatable("cover.ender_link.ui.private_no_access");
    private static final Component PERMISSION_TITLE = Component.translatable("cover.ender_link.ui.permission");
    private static final Component CHANNEL_NAME_TOOLTIP = Component.translatable("cover.ender_link.ui.channel_name.tooltip");
    private static final Component DESCRIPTION_PLACEHOLDER = Component.translatable("cover.ender_link.ui.description.placeholder");
    private static final Component DESCRIPTION_TOOLTIP = Component.translatable("cover.ender_link.ui.description.tooltip");
    private static final Component OPEN_LIST = Component.translatable("cover.ender_link.ui.open_list");
    private static final Component SECTION_TRANSFER = Component.translatable("cover.ui.transfer");
    private static final Component WORKING_TITLE = Component.translatable("cover.ender_link.ui.working");
    private static final Component IO_TITLE = Component.translatable("cover.ui.io");
    private static final Component MANUAL_IO_TITLE = Component.translatable("cover.ui.manual_io");
    private static final Component[] MANUAL_IO_TOOLTIPS = {
            Component.translatable("cover.universal.manual_import_export.mode.description.0"),
            Component.translatable("cover.universal.manual_import_export.mode.description.1"),
            Component.translatable("cover.universal.manual_import_export.mode.description.2")
    };
    private static final Component BACK_TOOLTIP = Component.translatable("cover.ender_link.ui.back");
    private static final Component LIST_PRIVATE = Component.translatable("cover.ender_link.ui.list.private");
    private static final Component LIST_PUBLIC = Component.translatable("cover.ender_link.ui.list.public");
    private static final Component LIST_EMPTY = Component.translatable("cover.ender_link.ui.list.empty");
    private static final Component BUTTON_CURRENT = Component.translatable("cover.ender_link.ui.current");
    private static final Component BUTTON_SELECT = Component.translatable("cover.ender_link.ui.select");
    private static final Component ALREADY_CURRENT = Component.translatable("cover.ender_link.ui.already_current");
    private static final Component NO_DESCRIPTION = Component.translatable("cover.ender_link.ui.no_description");
    private static final Component CLEAR_DESCRIPTION = Component.translatable("cover.ender_link.ui.clear_description");

    private static final int COLOR_LENGTH = 8;
    private static final int DESCRIPTION_MAX_LENGTH = 64;
    private static final int MAX_CHANNELS = 256;
    private static final int LIST_MAX_ROWS = 5;
    private static final int LIST_RESCAN_TICKS = 20;
    private static final int ROW_HEIGHT = 3 * TextLine.HEIGHT;
    private static final int LIST_WIDTH = UISizes.CONTENT_WIDTH - 2 * UISizes.PANEL_PADDING;
    private static final int ACCESS_CHECK_TICKS = 20;

    private EnderLinkUI() {}

    static Widget create(AbstractEnderLinkCover<?> cover) {
        var ctx = new Context(cover);
        var main = mainPage(ctx);
        var list = listPage(ctx);
        list.setDisplay(false);
        var root = Form.page();
        root.addSyncValue(SyncValue.ofBool(ctx::listShown).onChanged(shown -> {
            main.setDisplay(!shown);
            list.setDisplay(shown);
        }));
        ctx.root = root;
        return root.addChildren(main, list);
    }

    private static UIElement mainPage(Context ctx) {
        var cover = ctx.cover;
        var page = Form.page();
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
        var section = Form.section(SECTION_CHANNEL);
        section.disabled(() -> !ctx.canAccess(), PRIVATE_NO_ACCESS);

        section.addChild(CoverUIs.enumRow(PERMISSION_TITLE, List.of(AbstractEnderLinkCover.Permissions.values()),
                cover::getPermission, cover::setPermission));

        var name = TextField.of(0, () -> cover.colorStr, ctx::submitChannelName);
        name.layout(l -> l.flex(1));
        name.commitOnSubmit(text -> AbstractEnderLinkCover.COLOR_INPUT_PATTERN.matcher(text).matches());
        name.getInput().setMaxStringLength(COLOR_LENGTH);
        name.tooltips(CHANNEL_NAME_TOOLTIP);
        section.addChild(UIElement.centeredRow(UISizes.CONTROL_HEIGHT)
                .addChildren(EnderColorBlock.of(cover::getColor), name));

        var description = TextField.of(0, ctx::currentDescription, ctx::setCurrentDescription);
        description.layout(l -> l.flex(1));
        description.setPlaceholder(DESCRIPTION_PLACEHOLDER);
        description.getInput().setMaxStringLength(DESCRIPTION_MAX_LENGTH);
        description.tooltips(DESCRIPTION_TOOLTIP);
        section.addChild(UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.alignCenter()).addChild(description));

        section.addChild(Button.text(LayoutStyle.AUTO, OPEN_LIST).setOnServerClick(ctx::openList));
        return section;
    }

    private static UIElement transferSection(AbstractEnderLinkCover<?> cover) {
        var section = Form.section(SECTION_TRANSFER).addChildren(
                Form.controlRow(WORKING_TITLE, Switch.of(cover::isWorkingEnabled, cover::setWorkingEnabled)),
                CoverUIs.enumRow(IO_TITLE, List.of(IO.IN, IO.OUT), cover::getIo, cover::setIo, cover.ioTooltip()));
        if (cover.hasManualIO()) {
            section.addChild(CoverUIs.enumRow(MANUAL_IO_TITLE, List.of(ManualIOMode.VALUES),
                    cover::getManualIOMode, cover::setManualIOMode,
                    MANUAL_IO_TOOLTIPS));
        }
        return section;
    }

    private static UIElement listPage(Context ctx) {
        var cover = ctx.cover;
        var page = Form.page();
        var back = Button.icon(UITheme.ARROW_LEFT).setOnServerClick(ctx::closeList);
        back.tooltips(BACK_TOOLTIP);
        var title = TextLine.of(0, () -> cover.getPermission() == AbstractEnderLinkCover.Permissions.PRIVATE ?
                LIST_PRIVATE : LIST_PUBLIC);
        title.layout(l -> l.flex(1));
        page.addChild(UIElement.centeredRow(UISizes.CONTROL_HEIGHT).addChildren(back, title));

        var list = ServerList.of(ByteBufCodecs.STRING_UTF8, ctx::listNames, key -> channelRow(ctx, key))
                .version(ctx::revision).rescanEvery(LIST_RESCAN_TICKS).emptyText(LIST_EMPTY)
                .rowHeight(ROW_HEIGHT).maxRows(LIST_MAX_ROWS).scroll("ender_link.channels", LIST_WIDTH);
        page.addChild(UIElement.section().addChild(list));
        return page;
    }

    private static UIElement channelRow(Context ctx, String key) {
        var cover = ctx.cover;
        var row = UIElement.centeredRow(ROW_HEIGHT);
        var current = row.addSyncValue(SyncValue.ofBool(() -> ctx.isCurrent(key)));

        var colorText = key.substring(Math.min(key.length(), cover.identifier().length()));
        var name = TextLine.constant(LayoutStyle.AUTO, Component.literal(colorText)).bindClientColor(UITheme::panelText);
        var description = TextLine.of(LayoutStyle.AUTO, () -> ctx.descriptionOf(key)).bindClientColor(UITheme::textSecondary);
        var summary = TextLine.of(LayoutStyle.AUTO, () -> ctx.summaryOf(key)).bindClientColor(UITheme::textSecondary);
        var info = new UIElement().layout(l -> l.column().flex(1)).addChildren(name, description, summary);

        var select = Button.of(UISizes.BUTTON_WIDTH).bindClientText(() -> (current.getValue() ?
                BUTTON_CURRENT : BUTTON_SELECT).getString())
                .disabled(() -> ctx.isCurrent(key), ALREADY_CURRENT)
                .setOnServerClick(() -> ctx.select(key));
        var clear = Button.glyph("×")
                .disabled(() -> !ctx.hasDescription(key), NO_DESCRIPTION)
                .setOnServerClick(() -> ctx.clearDescription(key));
        clear.tooltips(CLEAR_DESCRIPTION);

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
