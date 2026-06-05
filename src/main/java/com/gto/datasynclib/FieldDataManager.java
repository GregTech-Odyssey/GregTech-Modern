package com.gto.datasynclib;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datasream.data.MapData;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

public final class FieldDataManager {

    public final IFieldDataHolder holder;
    public final FieldDefinitionStorage storage;
    private final Reference2ReferenceOpenHashMap<DataFieldDefinition<?>, DataField<?>> allField;
    private final DataField<?>[] syncToClientFields;
    private final DataField<?>[] syncToServerFields;
    private final DataField<?>[] saveFields;
    private boolean syncChange = true;
    private volatile boolean updateing;
    private volatile boolean writeing;

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

    public DataFieldDefinition<?> getFieldDefinition(Object fieldObject) {
        return getFieldDefinition(fieldObject.getClass(), fieldObject);
    }

    public DataFieldDefinition<?> getFieldDefinition(Class<?> type, Object fieldObject) {
        for (var definition : storage.typeDefinition.get(type)) {
            try {
                if (definition.get(definition.source.apply(holder)) == fieldObject) return definition;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public boolean hasSyncField(LogicalSide side) {
        var fields = side.isServer() ? syncToClientFields : syncToServerFields;
        return fields.length > 0;
    }

    public boolean hasSaveField() {
        return saveFields.length > 0;
    }

    /**
     * Marks specified fields as dirty for synchronization
     *
     * @param field the field definition to mark for sync
     */
    public void markFieldForSync(@NotNull DataFieldDefinition<?> field) {
        var f = allField.get(field);
        if (f != null) f.markAsChanged(field.source.apply(holder));
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
                if (f != null) f.markAsChanged(d.source.apply(holder));
            }
        }
    }

    public void clearAllFieldMark() {
        allField.values().forEach(f -> f.clearChanged(f.getDefinition().source.apply(holder)));
    }

    public void markAsChanged() {
        syncChange = true;
    }

    public void clearChanged() {
        syncChange = false;
    }

    public boolean isChanged() {
        return syncChange;
    }

    /**
     * Updates dirty flags for fields based on changes
     * 
     * @param side the logical side
     * @param auto whether to use auto-update mode
     * @return true if any changes were detected
     */
    public boolean updateFieldDirtyFlags(LogicalSide side, boolean auto) {
        if (updateing) return false;
        updateing = true;
        try {
            final var fields = side.isServer() ? syncToClientFields : syncToServerFields;
            boolean hasChanges = false;
            for (DataField<?> field : fields) {
                var d = field.getDefinition();
                var source = d.source.apply(holder);
                if (!field.mustDetected() && field.isChanged(source)) {
                    hasChanges = true;
                } else {
                    if ((!auto || d.autoUpdate(side)) && field.detectChange(side, d.source.apply(holder), auto)) {
                        hasChanges = true;
                    }
                }
            }
            return syncChange = hasChanges || syncChange;
        } finally {
            updateing = false;
        }
    }

    /**
     * Writes field data to network buffer
     * 
     * @param side  the logical side
     * @param force whether to force write all fields
     * @return byte array containing the serialized data
     */
    public byte @NotNull [] writeToNetworkBuffer(LogicalSide side, boolean force) {
        if (writeing) return ArrayUtils.EMPTY_BYTE_ARRAY;
        writeing = true;
        syncChange = false;
        var buf = Unpooled.buffer();
        var wrapper = new FriendlyByteBuf(buf);
        try {
            final var fields = side.isServer() ? syncToClientFields : syncToServerFields;
            holder.writeCustomSyncData(wrapper, force);
            for (int i = 0; i < fields.length; i++) {
                var field = fields[i];
                var d = field.getDefinition();
                var source = d.source.apply(holder);
                if (force || field.isChanged(source)) {
                    wrapper.writeVarInt(i);
                    field.writeToBuffer(side, source, wrapper, force);
                    field.clearChanged(source);
                }
            }
            buf.readerIndex(0);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return data;
        } finally {
            buf.release();
            writeing = false;
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
            var wrapper = new FriendlyByteBuf(buf);
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
        holder.writeCustomSaveData(data);
        for (var field : saveFields) {
            var d = field.getDefinition();
            data.put(d.key, field.writeToData(d.source.apply(holder)));
        }
        return data;
    }

    /**
     * Reads field data from MapData
     *
     * @param data the MapData to read from
     */
    public void readFromData(@NotNull MapData data, int dataVersion) {
        holder.readCustomSaveData(data, dataVersion);
        for (var field : saveFields) {
            var d = field.getDefinition();
            var tag = data.get(d.key);
            if (tag != null) field.readFromData(d.source.apply(holder), tag, dataVersion);
        }
    }
}
