package com.gregtechceu.gtceu.uipro.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.core.mixins.ldlib.WidgetAccessor;
import com.gregtechceu.gtceu.uipro.ILocalUI;

import com.lowdragmc.lowdraglib.gui.modular.WidgetUIAccess;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 单个控件的同步通道（对应 LDLib2 的 UISyncManager，作用域限于本控件）：S2C 状态、C2S 请求、服务端权威的结构状态共用一张 id 表，须在两端同序注册。
 * 自定义更新 / 请求 id 必须小于 {@link #BASE}。
 */
public final class UIChannel {

    public static final int BASE = 0x5A00;
    private static final int CAPACITY = 0xFF;
    private static final int ROUTE_ID = BASE + CAPACITY;

    private final Widget owner;
    private final List<Object> entries = new ArrayList<>(2);
    private final List<SyncValue<?>> values = new ArrayList<>(2);
    private final List<UIStructure<?>> structures = new ArrayList<>(0);
    private int shape = 1;
    private int epoch;
    private boolean initialDataSent;
    private boolean routesChildren;
    @Nullable
    private Boolean local;

    public UIChannel(Widget owner) {
        this.owner = owner;
    }

    public Widget getOwner() {
        return owner;
    }

    public static void sendRaw(Widget widget, int id, Consumer<FriendlyByteBuf> writer) {
        if (ILocalUI.isLocal(widget)) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                writer.accept(buf);
                widget.handleClientAction(id, buf);
            } finally {
                buf.release();
            }
            return;
        }
        if (!widget.isRemote() || widget.isClientSideWidget()) return;
        var access = ((WidgetAccessor) widget).gtceu$getUiAccess();
        if (access != null) access.writeClientAction(widget, id, writer);
    }

    public <T> SyncValue<T> addSyncValue(SyncValue<T> value) {
        register(value, 1);
        values.add(value);
        return value;
    }

    public <T> RPC<T> addRPC(ByteStreamCodec<T> codec, BiConsumer<Player, T> handler) {
        var rpc = new RPC<>(this, BASE + entries.size(), codec, handler);
        register(rpc, 2);
        return rpc;
    }

    public RPC<Unit> addRPC(Consumer<Player> handler) {
        return addRPC(UICodecs.UNIT, (player, unit) -> handler.accept(player));
    }

    public <K> UIStructure<K> addStructure(ByteStreamCodec<K> codec, Supplier<K> current) {
        var structure = new UIStructure<>(this, BASE + entries.size(), codec, current);
        register(structure, 3);
        structures.add(structure);
        routeChildrenByEpoch();
        return structure;
    }

    public <K> UIStructure<K> addStructure(ByteStreamCodec<K> codec, Supplier<K> current, Consumer<K> apply) {
        return addStructure(codec, current).apply(apply);
    }

    public <T> Binding<T> addBinding(Binding<T> binding) {
        binding.register(this);
        return binding;
    }

    public <T> UIEvent<T> addEvent(ByteStreamCodec<T> codec, Consumer<T> handler) {
        var event = new UIEvent<>(this, BASE + entries.size(), codec, handler);
        register(event, 4);
        return event;
    }

    public void routeChildrenByEpoch() {
        if (routesChildren) return;
        routesChildren = true;
        if (owner instanceof WidgetGroup group) {
            for (var child : group.widgets) onChildAdded(child);
        }
    }

    public void onChildAdded(Widget child) {
        if (!routesChildren || child.isClientSideWidget()) return;
        var original = ((WidgetAccessor) child).gtceu$getUiAccess();
        if (original != null && !(original instanceof EpochAccess)) child.setUiAccess(new EpochAccess(original));
    }

    public void prime() {
        if (owner.isRemote() || owner.isClientSideWidget()) return;
        for (var value : values) value.prime();
    }

    public void writeHead(FriendlyByteBuf buf) {
        if (structures.isEmpty() && !routesChildren) {
            buf.writeByte(0);
            return;
        }
        UIFrames.write(buf, frame -> {
            frame.writeByte(shape);
            frame.writeVarInt(epoch);
            for (var structure : structures) structure.writeState(frame);
        });
    }

    public void readHead(FriendlyByteBuf buf) {
        UIFrames.read(buf, frame -> {
            byte serverShape = frame.readByte();
            if (serverShape != (byte) shape) {
                GTCEu.LOGGER.error("UI channel shape mismatch in {}: entries registered differently on server and client", UIFaults.where(owner));
                frame.skipBytes(frame.readableBytes());
                return;
            }
            int serverEpoch = frame.readVarInt();
            for (var structure : structures) structure.readState(frame);
            epoch = serverEpoch;
        }, owner);
    }

    public void writeTail(FriendlyByteBuf buf) {
        initialDataSent = true;
        if (values.isEmpty() || owner.isClientSideWidget()) {
            buf.writeByte(0);
            return;
        }
        UIFrames.write(buf, frame -> {
            frame.writeByte(shape);
            for (var value : values) value.writeInitial(frame);
        });
    }

    public void readTail(FriendlyByteBuf buf) {
        UIFrames.read(buf, frame -> {
            if (frame.readByte() != (byte) shape) {
                GTCEu.LOGGER.error("UI channel shape mismatch in {}: entries registered differently on server and client", UIFaults.where(owner));
                frame.skipBytes(frame.readableBytes());
                return;
            }
            for (var value : values) value.readInitial(frame);
        }, owner);
    }

    public void writeInitialData(FriendlyByteBuf buf) {
        writeHead(buf);
        writeTail(buf);
    }

    public void readInitialData(FriendlyByteBuf buf) {
        readHead(buf);
        readTail(buf);
    }

    public void detectAndSendChanges() {
        if (values.isEmpty() || owner.isClientSideWidget()) return;
        for (int i = 0, size = entries.size(); i < size; i++) {
            if (entries.get(i) instanceof SyncValue<?> value && value.detectChange()) sendToClient(BASE + i, value::write);
        }
    }

    public boolean readUpdateInfo(int id, FriendlyByteBuf buf) {
        if (id == ROUTE_ID) {
            epoch = buf.readVarInt();
            return true;
        }
        int index = id - BASE;
        if (index < 0 || index >= entries.size()) return false;
        var entry = entries.get(index);
        if (entry instanceof SyncValue<?> value) value.read(buf);
        else if (entry instanceof UIStructure<?> structure) structure.receive(buf);
        else if (entry instanceof RPC<?> rpc) rpc.receiveReply(buf);
        else if (entry instanceof UIEvent<?> event) event.receive(buf);
        return true;
    }

    public boolean handleClientAction(int id, FriendlyByteBuf buf) {
        if (routesChildren) {
            if (id == ROUTE_ID) routeToChild(buf);
            if (id == ROUTE_ID || id == 1) return true;
        }
        int index = id - BASE;
        if (index < 0 || index >= entries.size()) return false;
        var entry = entries.get(index);
        if (entry instanceof RPC<?> rpc) rpc.receive(buf);
        else if (entry instanceof UIStructure<?> structure) structure.request().receive(buf);
        return true;
    }

    public void pollClient() {
        if (values.isEmpty() || !isLocal()) return;
        for (var value : values) value.pollLocal();
    }

    public int getEpoch() {
        return epoch;
    }

    public void advanceEpoch() {
        if (isLocal()) {
            epoch++;
        } else if (!owner.isRemote()) {
            int next = ++epoch;
            sendToClient(ROUTE_ID, buf -> buf.writeVarInt(next));
        }
    }

    int bumpEpoch() {
        return ++epoch;
    }

    void setEpoch(int epoch) {
        this.epoch = epoch;
    }

    boolean isInitialDataSent() {
        return initialDataSent;
    }

    boolean isLocal() {
        if (local == null) {
            boolean result = ILocalUI.isLocal(owner);
            if (owner.getGui() == null) return result;
            local = result;
        }
        return local;
    }

    @Nullable
    Player player() {
        var gui = owner.getGui();
        return gui == null ? null : gui.entityPlayer;
    }

    void sendToServer(int id, Consumer<FriendlyByteBuf> writer) {
        var access = ((WidgetAccessor) owner).gtceu$getUiAccess();
        if (access != null && owner.isRemote() && !owner.isClientSideWidget()) access.writeClientAction(owner, id, writer);
    }

    void sendToClient(int id, Consumer<FriendlyByteBuf> writer) {
        if (!initialDataSent || owner.isRemote()) return;
        var access = ((WidgetAccessor) owner).gtceu$getUiAccess();
        if (access != null && owner.getGui() != null) access.writeUpdateInfo(owner, id, writer);
    }

    private void register(Object entry, int kind) {
        if (entries.size() >= CAPACITY) throw new IllegalStateException("Too many UI channel entries in " + UIFaults.where(owner));
        entries.add(entry);
        shape = shape * 31 + kind;
    }

    private void routeToChild(FriendlyByteBuf buf) {
        if (owner.isRemote() || !(owner instanceof WidgetGroup group) || !buf.isReadable()) return;
        int clientEpoch = buf.readVarInt();
        int index = buf.readVarInt();
        int updateId = buf.readVarInt();
        if (clientEpoch != epoch || index < 0 || index >= group.widgets.size()) return;
        group.widgets.get(index).handleClientAction(updateId, buf);
    }

    public interface Host {

        UIChannel getChannel();

        default <T> SyncValue<T> addSyncValue(SyncValue<T> value) {
            return getChannel().addSyncValue(value);
        }

        default <T> RPC<T> addRPC(ByteStreamCodec<T> codec, BiConsumer<Player, T> handler) {
            return getChannel().addRPC(codec, handler);
        }

        default RPC<Unit> addRPC(Consumer<Player> handler) {
            return getChannel().addRPC(handler);
        }

        default <K> UIStructure<K> addStructure(ByteStreamCodec<K> codec, Supplier<K> current) {
            return getChannel().addStructure(codec, current);
        }

        default <K> UIStructure<K> addStructure(ByteStreamCodec<K> codec, Supplier<K> current, Consumer<K> apply) {
            return getChannel().addStructure(codec, current, apply);
        }

        default <T> Binding<T> addBinding(Binding<T> binding) {
            return getChannel().addBinding(binding);
        }

        default <T> UIEvent<T> addEvent(ByteStreamCodec<T> codec, Consumer<T> handler) {
            return getChannel().addEvent(codec, handler);
        }
    }

    private final class EpochAccess implements WidgetUIAccess {

        private final WidgetUIAccess original;

        private EpochAccess(WidgetUIAccess original) {
            this.original = original;
        }

        @Override
        public boolean attemptMergeStack(ItemStack itemStack, boolean fromContainer, boolean simulate) {
            return original.attemptMergeStack(itemStack, fromContainer, simulate);
        }

        @Override
        public void writeClientAction(Widget widget, int updateId, Consumer<FriendlyByteBuf> dataWriter) {
            if (!(owner instanceof WidgetGroup group)) return;
            int index = group.widgets.indexOf(widget);
            if (index < 0) return;
            int sentEpoch = epoch;
            sendToServer(ROUTE_ID, buf -> {
                buf.writeVarInt(sentEpoch);
                buf.writeVarInt(index);
                buf.writeVarInt(updateId);
                dataWriter.accept(buf);
            });
        }

        @Override
        public void writeUpdateInfo(Widget widget, int updateId, Consumer<FriendlyByteBuf> dataWriter) {
            original.writeUpdateInfo(widget, updateId, dataWriter);
        }
    }
}
