package com.gregtechceu.gtceu.common.cover.ender;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class EnderChannelRows extends UIElement {

    private static final int ROWS_ID = SyncValueHost.ID_BASE - 16;
    private static final int RESCAN_TICKS = 20;

    private final boolean remote;
    private final Supplier<List<String>> source;
    private final IntSupplier revision;
    private final Function<String, UIElement> rowFactory;
    private final Component emptyText;
    private final List<String> keys = new ArrayList<>();
    private boolean built;
    private int builtRevision;
    private int ticks;

    EnderChannelRows(boolean remote, Supplier<List<String>> source, IntSupplier revision,
                     Function<String, UIElement> rowFactory, Component emptyText) {
        this.remote = remote;
        this.source = source;
        this.revision = revision;
        this.rowFactory = rowFactory;
        this.emptyText = emptyText;
        layout(l -> l.column().gapAll(UISizes.GAP));
        updateEmptyHeight();
    }

    @Override
    public void initWidget() {
        if (!remote && !built) {
            built = true;
            builtRevision = revision.getAsInt();
            for (var key : source.get()) addRow(key);
            updateEmptyHeight();
        }
        super.initWidget();
    }

    private void addRow(String key) {
        keys.add(key);
        addWidget(rowFactory.apply(key));
    }

    private void setRowVisible(int index, boolean visible) {
        ((UIElement) widgets.get(index)).setDisplay(visible);
    }

    private void updateEmptyHeight() {
        layout(l -> l.minHeight(hasVisibleRow() ? 0 : TextLine.HEIGHT));
    }

    private boolean hasVisibleRow() {
        for (var widget : widgets) {
            if (widget instanceof UIElement row && row.isDisplayed()) return true;
        }
        return false;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        if (hasVisibleRow()) return;
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, UITheme.clip(font, emptyText.getString(), getSizeWidth()), getPositionX(), getPositionY(), UITheme.TEXT_SECONDARY, false);
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        buffer.writeVarInt(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            buffer.writeUtf(keys.get(i));
            buffer.writeBoolean(((UIElement) widgets.get(i)).isDisplayed());
        }
        super.writeInitialData(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        for (int i = 0; i < size; i++) {
            addRow(buffer.readUtf());
            setRowVisible(i, buffer.readBoolean());
        }
        updateEmptyHeight();
        super.readInitialData(buffer);
    }

    @Override
    public void detectAndSendChanges() {
        if (!remote && built) {
            int current = revision.getAsInt();
            if (current != builtRevision || ++ticks >= RESCAN_TICKS) {
                builtRevision = current;
                ticks = 0;
                syncRows();
            }
        }
        super.detectAndSendChanges();
    }

    private void syncRows() {
        var latest = new LinkedHashSet<>(source.get());
        var visible = new boolean[keys.size()];
        boolean changed = false;
        for (int i = 0; i < keys.size(); i++) {
            visible[i] = latest.remove(keys.get(i));
            if (visible[i] != ((UIElement) widgets.get(i)).isDisplayed()) changed = true;
        }
        var added = new ArrayList<>(latest);
        if (!changed && added.isEmpty()) return;
        writeUpdateInfo(ROWS_ID, buffer -> {
            buffer.writeVarInt(visible.length);
            for (var value : visible) buffer.writeBoolean(value);
            buffer.writeVarInt(added.size());
            for (var key : added) buffer.writeUtf(key);
        });
        apply(visible, added);
    }

    private void apply(boolean[] visible, List<String> added) {
        for (int i = 0; i < visible.length; i++) setRowVisible(i, visible[i]);
        for (var key : added) addRow(key);
        updateEmptyHeight();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id != ROWS_ID) {
            super.readUpdateInfo(id, buffer);
            return;
        }
        var visible = new boolean[buffer.readVarInt()];
        for (int i = 0; i < visible.length; i++) visible[i] = buffer.readBoolean();
        int count = buffer.readVarInt();
        var added = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) added.add(buffer.readUtf());
        apply(visible, added);
    }
}
