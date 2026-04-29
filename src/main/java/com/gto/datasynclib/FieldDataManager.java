package com.gto.datasynclib;

import com.gto.datasynclib.datasream.data.MapData;
import com.gto.datasynclib.datasream.stream.ByteBufWrapper;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class FieldDataManager {

    private final IFieldDataHolder holder;
    private final FieldDefinitionStorage storage;
    private final Reference2ReferenceOpenHashMap<DataFieldDefinition<?>, DataField<?>> allField;
    private final DataField<?>[] syncToClientFields;
    private final DataField<?>[] syncToServerFields;
    private final DataField<?>[] saveFields;

    public FieldDataManager(IFieldDataHolder holder) {
        this.holder = holder;
        this.storage = FieldDefinitionStorage.get(holder.getClass());
        this.allField = new Reference2ReferenceOpenHashMap<>(storage.allDefinition.size());
        storage.allDefinition.values().forEach(d -> allField.put(d, d.factory.create(d)));
        syncToClientFields = new DataField[storage.syncToClientDefinitions.length];
        for (int i = 0; i < syncToClientFields.length; i++) {
            syncToClientFields[i] = allField.get(storage.syncToClientDefinitions[i]);
        }
        syncToServerFields = new DataField[storage.syncToServerDefinitions.length];
        for (int i = 0; i < syncToServerFields.length; i++) {
            syncToServerFields[i] = allField.get(storage.syncToServerDefinitions[i]);
        }
        saveFields = new DataField[storage.saveDefinitions.length];
        for (int i = 0; i < saveFields.length; i++) {
            saveFields[i] = allField.get(storage.saveDefinitions[i]);
        }
    }

    public boolean hasSyncToClientField() {
        return syncToClientFields.length > 0;
    }

    public boolean hasSyncToServerField() {
        return syncToServerFields.length > 0;
    }

    public boolean hasSaveField() {
        return saveFields.length > 0;
    }

    /**
     * Marks specified fields as dirty for synchronization
     *
     * @param fields the field names to mark for sync
     */
    public void markFieldsForSync(@NotNull String... fields) {
        for (var field : fields) {
            var d = storage.allDefinition.get(field);
            if (d != null) {
                var f = allField.get(d);
                if (f != null) f.markAsDirty();
            }
        }
    }

    /**
     * Updates dirty flags for fields based on changes
     * 
     * @param side the logical side
     * @param auto whether to use auto-update mode
     * @return true if any changes were detected
     */
    public boolean updateSyncDirtyFlags(LogicalSide side, boolean auto) {
        final var fields = side.isServer() ? syncToClientFields : syncToServerFields;
        boolean hasChanges = false;
        for (DataField<?> field : fields) {
            if (field.isDirty()) {
                hasChanges = true;
            } else {
                var d = field.getDefinition();
                if ((!auto || d.autoUpdate(side)) && field.hasChanges(side, d.source.apply(holder), auto)) {
                    field.markAsDirty();
                    hasChanges = true;
                }
            }
        }
        return hasChanges;
    }

    /**
     * Writes field data to network buffer
     * 
     * @param side  the logical side
     * @param force whether to force write all fields
     * @return byte array containing the serialized data
     */
    public byte @NotNull [] writeToNetworkBuffer(LogicalSide side, boolean force) {
        var buf = Unpooled.buffer();
        var wrapper = new ByteBufWrapper(buf);
        try {
            final var fields = side.isServer() ? syncToClientFields : syncToServerFields;
            holder.writeCustomSyncData(wrapper, force);
            for (int i = 0; i < fields.length; i++) {
                var field = fields[i];
                var d = field.getDefinition();
                if (force || field.isDirty()) {
                    wrapper.writeVarInt(i);
                    field.writeToBuffer(side, d.source.apply(holder), wrapper, force);
                    field.clearDirty();
                }
            }
            buf.readerIndex(0);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return data;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            buf.release();
        }
    }

    /**
     * Reads field data from network buffer
     * 
     * @param side the logical side
     * @param data the byte array to read from
     */
    public void readFromNetworkBuffer(LogicalSide side, byte @NotNull [] data) {
        if (data.length > 0) {
            var buf = Unpooled.wrappedBuffer(data);
            var wrapper = new ByteBufWrapper(buf);
            try {
                final var fields = side.isClient() ? syncToClientFields : syncToServerFields;
                holder.readCustomSyncData(wrapper);
                boolean update = false;
                while (buf.readableBytes() > 0) {
                    var f = fields[wrapper.readVarInt()];
                    var d = f.getDefinition();
                    f.readFromBuffer(side, d.source.apply(holder), wrapper);
                    if (d.notifyUpdate(side)) {
                        update = true;
                    }
                }
                if (update) holder.scheduleUpdate(side);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                buf.release();
            }
        }
    }

    /**
     * Writes field data to MapData
     * 
     * @return MapData containing all saveable field data
     */
    @NotNull
    public MapData writeToData() {
        MapData data = new MapData();
        for (var field : saveFields) {
            var d = field.getDefinition();
            data.put(d.key, field.writeToData(d.source.apply(holder)));
        }
        holder.writeCustomSaveData(data);
        return data;
    }

    /**
     * Reads field data from MapData
     * 
     * @param data the MapData to read from
     */
    public void readFromData(@NotNull MapData data) {
        for (var field : saveFields) {
            var d = field.getDefinition();
            var tag = data.get(d.key);
            if (tag != null) field.readFromData(d.source.apply(holder), tag);
        }
        holder.readCustomSaveData(data);
    }
}
