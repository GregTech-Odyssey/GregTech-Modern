package com.gregtechceu.gtceu.common.cover.ender;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.misc.virtualregistry.entries.VirtualRedstone;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.core.Direction;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;

import java.util.UUID;

public class EnderRedstoneLinkCover extends AbstractEnderLinkCover<VirtualRedstone> {

    @SaveToDisk
    @SyncToClient
    private VirtualRedstone storage;
    @SaveToDisk
    @SyncToClient
    private UUID uuid;

    public EnderRedstoneLinkCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
        // 不在建構子註冊頻道：覆蓋板從 NBT 載入時是「先跑建構子、之後才還原 uuid/colorStr」，
        // 此時 colorStr 還是預設的 FFFFFFFF、uuid 也還沒還原，
        // 在這裡註冊等於每次區塊載入都往預設頻道塞一個拋棄式 UUID，而且永遠不會被移除。
        // 改到 onLoad()（NBT 已還原、level 也已設定）再註冊。
        uuid = null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (isRemote()) return;
        if (uuid == null) uuid = UUID.randomUUID();
        setVirtualEntry();
    }

    @Override
    protected String identifier() {
        return "ERLink#";
    }

    @Override
    protected VirtualRedstone getEntry() {
        var storage = this.storage;
        if (storage == null) return new VirtualRedstone();
        return storage;
    }

    @Override
    protected void setEntry(VirtualEntry entry) {
        if (storage != null) storage.removeMember(uuid);
        storage = (VirtualRedstone) entry;
        storage.addMember(uuid);
    }

    @Override
    protected EntryTypes<VirtualRedstone> getEntryType() {
        return EntryTypes.ENDER_REDSTONE;
    }

    @Override
    protected void transfer() {
        switch (io) {
            case IN -> storage.setSignal(uuid, getSignalInput());
            case OUT -> setRedstoneSignalOutput(storage.getSignal());
        }
    }

    @Override
    protected Widget addVirtualEntryWidget(VirtualEntry entry, int x, int y, int width, int height, boolean canClick) {
        return new WidgetGroup(x, y, width, height);
    }

    @Override
    protected String getUITitle() {
        return "cover.ender_redstone_link.title";
    }

    @Override
    public boolean canConnectRedstone() {
        return true;
    }

    @Override
    public void onRemoved() {
        if (!isRemote() && storage != null) storage.removeMember(uuid);
        super.onRemoved();
    }

    @Override
    public void onUnload() {
        // 區塊卸載時也要退出頻道，否則本板子最後讀到的值會永遠留在頻道裡，
        // 讓其他 OUT 板子一直讀到一個早已沒有來源的訊號。
        if (!isRemote() && storage != null) storage.removeMember(uuid);
        super.onUnload();
    }

    @Override
    protected void onTransferStopped() {
        if (isRemote()) return;
        // 離開 OUT：redstoneSignalOutput 只有 transfer() 的 OUT 分支會寫，
        // 不在這裡歸零的話機器那一面會永遠卡在最後一次的輸出值。
        setRedstoneSignalOutput(0);
        // 離開 IN：不再由本板子撐住頻道值。
        if (storage != null) storage.setSignal(uuid, 0);
    }

    protected int getSignalInput() {
        // Level#getSignal 的 Direction 是「接收方 -> 來源」的方向，也就是 attachedSide 本身；
        // 傳 getOpposite() 會讀成別的面（對照 MachineControllerCover#getInputSignal）。
        return coverHolder.getLevel().getSignal(coverHolder.getPos().relative(attachedSide),
                attachedSide);
    }
}
