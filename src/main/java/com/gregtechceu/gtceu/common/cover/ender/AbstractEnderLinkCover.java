package com.gregtechceu.gtceu.common.cover.ender;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.MachineCoverContainer;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEnderRegistry;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

@SuppressWarnings("SameParameterValue")
public abstract class AbstractEnderLinkCover<T extends VirtualEntry> extends CoverBehavior implements IUICover, IControllable {

    public static final Pattern COLOR_INPUT_PATTERN = Pattern.compile("^[0-9a-fA-F]{0,8}$");
    protected final ConditionalSubscriptionHandler subscriptionHandler;
    @SaveToDisk(defaultValue = "FFFFFFFF")
    @SyncToClient
    protected String colorStr = VirtualEntry.DEFAULT_COLOR;
    @Getter
    @SaveToDisk(defaultValue = "PUBLIC")
    @SyncToClient
    protected Permissions permission = Permissions.PUBLIC;
    @Getter
    @SaveToDisk(defaultValue = "true")
    protected boolean isWorkingEnabled = true;
    @Getter
    @SaveToDisk(defaultValue = "DISABLED")
    @SyncToClient
    protected ManualIOMode manualIOMode = ManualIOMode.DISABLED;
    @Getter
    @SaveToDisk(defaultValue = "OUT")
    @SyncToClient(scheduleUpdate = true)
    protected IO io = IO.OUT;

    public AbstractEnderLinkCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
        subscriptionHandler = new ConditionalSubscriptionHandler(coverHolder, this::update, 20, this::isSubscriptionActive);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        subscriptionHandler.initialize(coverHolder.getLevel());
    }

    @Override
    public boolean canAttach() {
        return super.canAttach() && MetaMachine.getMachine(coverHolder.holder()) != null;
    }

    @Override
    public void onAttached(@NotNull ItemStack itemStack, @NotNull ServerPlayer player) {
        super.onAttached(itemStack, player);
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        subscriptionHandler.unsubscribe();
        if (!isRemote()) {
            VirtualEnderRegistry.getInstance().deleteEntryIf(getOwner(), getEntryType(), getChannelName(), VirtualEntry::canRemove);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        subscriptionHandler.unsubscribe();
        if (!isRemote()) {
            VirtualEnderRegistry.getInstance().deleteEntryIf(getOwner(), getEntryType(), getChannelName(), VirtualEntry::canRemove);
        }
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (this.isWorkingEnabled != isWorkingAllowed) {
            this.isWorkingEnabled = isWorkingAllowed;
            if (!isWorkingAllowed) onTransferStopped();
            subscriptionHandler.updateSubscription();
            coverHolder.onChanged();
        }
    }

    /**
     * 傳輸停止時（切換 IO 模式、或關閉工作開關）呼叫。
     * update() 之後不會再跑，子類別必須在這裡清掉自己殘留的輸出與頻道值，否則會被永久凍結在最後一次的狀態。
     */
    protected void onTransferStopped() {}

    @Override
    public Widget createUIWidget() {
        return EnderLinkUI.create(this);
    }

    public void setIo(IO io) {
        if (io == IO.IN || io == IO.OUT) {
            if (this.io != io) onTransferStopped();
            this.io = io;
            subscriptionHandler.updateSubscription();
        }
    }

    public UUID getOwner() {
        if (permission == Permissions.PRIVATE && coverHolder instanceof MachineCoverContainer mcc) {
            var owner = mcc.getMachine().getOwner();
            return owner != null ? owner.getPlayerUUID() : null;
        }
        return null;
    }

    protected boolean isSubscriptionActive() {
        return isWorkingEnabled;
    }

    protected abstract String identifier();

    protected abstract VirtualEntry getEntry();

    protected abstract void setEntry(VirtualEntry entry);

    protected final String getChannelName() {
        return identifier() + this.colorStr;
    }

    protected void setChannelName(String name) {
        if (isRemote()) return;
        var oldChannel = getChannelName();
        this.colorStr = name;
        // 先加入新頻道（setEntry 會順便把自己從舊 entry 移除），再回頭清舊 entry；
        // 反過來做的話舊頻道永遠會因為「自己還在成員裡」而通不過 canRemove()。
        setVirtualEntry();
        VirtualEnderRegistry.getInstance().deleteEntryIf(getOwner(), getEntryType(), oldChannel, VirtualEntry::canRemove);
    }

    protected final String getChannelName(VirtualEntry entry) {
        return identifier() + entry.getColorStr();
    }

    protected void setPermission(Permissions permission) {
        if (isRemote()) return;
        var oldOwner = getOwner();
        var oldChannel = getChannelName();
        this.permission = permission;
        setVirtualEntry();
        VirtualEnderRegistry.getInstance().deleteEntryIf(oldOwner, getEntryType(), oldChannel, VirtualEntry::canRemove);
    }

    protected void setVirtualEntry() {
        setEntry(VirtualEnderRegistry.getInstance().getOrCreateEntry(getOwner(), getEntryType(), getChannelName()));
        getEntry().setColor(this.colorStr);
        subscriptionHandler.updateSubscription();
    }

    protected abstract EntryTypes<T> getEntryType();

    protected void update() {
        if (isWorkingEnabled && !isRemote()) {
            var entry = VirtualEnderRegistry.getInstance().getOrCreateEntry(getOwner(), getEntryType(), getChannelName());
            if (!entry.getColorStr().equals(this.colorStr)) {
                entry.setColor(this.colorStr);
            }
            if (!getEntry().equals(entry)) {
                setEntry(entry);
            }
            transfer();
        }
        subscriptionHandler.updateSubscription();
    }

    protected abstract void transfer();

    protected void setManualIOMode(ManualIOMode manualIOMode) {
        this.manualIOMode = manualIOMode;
        subscriptionHandler.updateSubscription();
    }

    @Nullable
    protected FilterHandler<?, ?> getFilterHandler() {
        return null;
    }

    protected boolean hasManualIO() {
        return true;
    }

    protected String ioTooltipKey() {
        return "cover.ender_link.ui.io.tooltip";
    }

    protected abstract void addEntryStatus(StatusPanel panel, BooleanSupplier visible);

    protected abstract Component describeEntry(T entry);

    @SuppressWarnings("unchecked")
    final Component summaryOf(VirtualEntry entry) {
        return describeEntry((T) entry);
    }

    protected int getColor() {
        return VirtualEntry.parseColor(this.colorStr);
    }

    @Getter
    protected enum Permissions implements EnumSelectorWidget.SelectableEnum {

        PUBLIC("cover.ender_link.permission.public", WidgetIcons.ACCESS_PUBLIC),
        PRIVATE("cover.ender_link.permission.private", WidgetIcons.ACCESS_PRIVATE);

        private final String tooltip;
        private final IGuiTexture icon;

        Permissions(String tooltip, IGuiTexture icon) {
            this.tooltip = tooltip;
            this.icon = icon;
        }
    }
}
