package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.network.packets.CPacketBuildUpload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;

public final class BuildUpload {

    public static final int MAX_CELLS = 1 << 20;
    public static final int MAX_PALETTE = 4096;
    public static final int MAX_VALUES = 256;
    public static final int MAX_PAYLOAD = 30000;
    private static final int CHUNK_HEADER = 32;
    private static final int EXPIRE_TICKS = 200;

    private static final Object2ObjectOpenHashMap<UUID, Upload> UPLOADS = new Object2ObjectOpenHashMap<>();
    private static int lastId;

    static {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedOutEvent.class,
                event -> UPLOADS.remove(event.getEntity().getUUID()));
    }

    private BuildUpload() {}

    private static final class Upload {

        private final int id;
        private final MultiblockMachineDefinition definition;
        private final int[] values;
        private final Item[] palette;
        private final Item[] choices;
        private int filled;
        private int touched;

        private Upload(int id, MultiblockMachineDefinition definition, int[] values, Item[] palette, int cells, int now) {
            this.id = id;
            this.definition = definition;
            this.values = values;
            this.palette = palette;
            this.choices = new Item[cells];
            this.touched = now;
        }
    }

    public static int send(MultiblockMachineDefinition definition, int[] values, Item[] choices) {
        if (choices.length > MAX_CELLS || values.length > MAX_VALUES) return -1;
        var indices = new Reference2IntLinkedOpenHashMap<Item>();
        for (var item : choices) {
            if (item != null) indices.putIfAbsent(item, indices.size() + 1);
        }
        if (indices.size() > MAX_PALETTE) return -1;
        var palette = new Item[indices.size() + 1];
        for (var it = indices.reference2IntEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            palette[entry.getIntValue()] = entry.getKey();
        }
        int id = ++lastId;
        if (id <= 0) id = lastId = 1;
        GTNetwork.NETWORK.sendToServer(CPacketBuildUpload.begin(id, definition.getId(), values, choices.length, palette));
        var runs = new IntArrayList();
        int budget = MAX_PAYLOAD - CHUNK_HEADER;
        int offset = 0, bytes = 0;
        for (int i = 0; i < choices.length;) {
            var item = choices[i];
            int index = item == null ? 0 : indices.getInt(item);
            int end = i + 1;
            while (end < choices.length && choices[end] == item) end++;
            int length = end - i;
            int size = FriendlyByteBuf.getVarIntSize(index) + FriendlyByteBuf.getVarIntSize(length);
            if (bytes + size > budget) {
                GTNetwork.NETWORK.sendToServer(CPacketBuildUpload.cells(id, offset, runs.toIntArray()));
                offset = i;
                bytes = 0;
                runs.clear();
            }
            runs.add(index);
            runs.add(length);
            bytes += size;
            i = end;
        }
        if (!runs.isEmpty()) GTNetwork.NETWORK.sendToServer(CPacketBuildUpload.cells(id, offset, runs.toIntArray()));
        return id;
    }

    public static void receiveBegin(ServerPlayer player, int id, ResourceLocation definitionId, int[] values, int cells, Item[] palette) {
        var key = player.getUUID();
        UPLOADS.remove(key);
        if (cells < 0 || cells > MAX_CELLS || palette.length == 0 || palette.length > MAX_PALETTE + 1 || values.length > MAX_VALUES) return;
        if (!(GTRegistries.MACHINES.get(definitionId) instanceof MultiblockMachineDefinition definition)) return;
        UPLOADS.put(key, new Upload(id, definition, values, palette, cells, player.server.getTickCount()));
    }

    public static void receiveCells(ServerPlayer player, int id, int offset, int[] runs) {
        var key = player.getUUID();
        var upload = UPLOADS.get(key);
        if (upload == null || upload.id != id) return;
        int now = player.server.getTickCount();
        if (now - upload.touched > EXPIRE_TICKS || offset != upload.filled || (runs.length & 1) != 0) {
            UPLOADS.remove(key);
            return;
        }
        int total = upload.choices.length, cursor = offset;
        for (int r = 0; r < runs.length; r += 2) {
            int index = runs[r], length = runs[r + 1];
            if (index < 0 || index >= upload.palette.length || length <= 0 || length > total - cursor) {
                UPLOADS.remove(key);
                return;
            }
            cursor += length;
        }
        cursor = offset;
        for (int r = 0; r < runs.length; r += 2) {
            int length = runs[r + 1];
            Arrays.fill(upload.choices, cursor, cursor + length, upload.palette[runs[r]]);
            cursor += length;
        }
        upload.filled = cursor;
        upload.touched = now;
    }

    @Nullable
    public static Item[] take(ServerPlayer player, int id, MultiblockMachineDefinition definition, int[] values, int expectedCells) {
        var key = player.getUUID();
        var upload = UPLOADS.get(key);
        if (upload == null || upload.id != id) return null;
        UPLOADS.remove(key);
        if (player.server.getTickCount() - upload.touched > EXPIRE_TICKS) return null;
        if (upload.filled != upload.choices.length || upload.choices.length != expectedCells) return null;
        if (upload.definition != definition || !Arrays.equals(upload.values, values)) return null;
        return upload.choices;
    }
}
