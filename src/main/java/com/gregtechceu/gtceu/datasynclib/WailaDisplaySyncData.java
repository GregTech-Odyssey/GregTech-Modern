package com.gregtechceu.gtceu.datasynclib;

import com.gregtechceu.gtceu.api.capability.annotation.WailaDisplaySync;

import net.minecraft.nbt.CompoundTag;

import com.gto.datasynclib.*;
import com.gto.datasynclib.util.ByteBufUtil;
import org.jetbrains.annotations.Nullable;

public final class WailaDisplaySyncData {

    public static final String KEY = "waila_display_field_sync";

    private WailaDisplaySyncData() {}

    public static void init() {
        FieldDefinitionStorage.registerAnnotation(WailaDisplaySync.class);
    }

    public static void write(Object provider, CompoundTag data) {
        var holder = holder(provider);
        if (holder == null) return;
        var manager = holder.getFieldDataManager();
        var fields = fields(manager);
        if (fields.length == 0) return;
        var value = ByteBufUtil.writeToBytes(ByteBufUtil.FRIENDLY_BYTE_BUF_WRAPPER, buf -> manager.writeFieldsToBuffer(LogicalSide.SERVER, buf, fields));
        if (value.length == 0) return;
        data.putByteArray(KEY, value);
    }

    public static void read(Object provider, CompoundTag data) {
        var holder = holder(provider);
        if (holder == null) return;
        var bytes = data.getByteArray(KEY);
        if (bytes.length == 0) return;
        var manager = holder.getFieldDataManager();
        var fields = fields(manager);
        if (fields.length == 0) return;
        ByteBufUtil.readFromBytes(ByteBufUtil.FRIENDLY_BYTE_BUF_WRAPPER, bytes, buf -> manager.readFieldsFromBuffer(LogicalSide.CLIENT, buf, fields));
    }

    private static DataFieldDefinition<?>[] fields(FieldDataManager manager) {
        return manager.storage.getAnnotatedFieldDefinitions(WailaDisplaySync.class);
    }

    private static @Nullable IFieldDataHolder holder(Object provider) {
        return provider instanceof IFieldDataHolder holder ? holder : null;
    }
}
