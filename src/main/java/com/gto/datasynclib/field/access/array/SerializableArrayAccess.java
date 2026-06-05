package com.gto.datasynclib.field.access.array;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.IDataSerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ListData;
import com.gto.datasynclib.datasream.data.MapData;
import com.gto.datasynclib.datasream.data.NullData;
import com.gto.datasynclib.field.access.AbstractFieldAccess;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;

public final class SerializableArrayAccess extends AbstractFieldAccess<IDataSerializable[]> {

    private int hashCode;

    public SerializableArrayAccess(DataFieldDefinition<IDataSerializable[]> definition) {
        super(definition);
    }

    @Override
    protected boolean hasChange(@NotNull LogicalSide side, IDataSerializable @NotNull [] instance, boolean auto) {
        var hashCode = Arrays.hashCode(instance);
        if (hashCode != this.hashCode) {
            this.hashCode = hashCode;
            for (var element : instance) {
                element.markAsChanged();
            }
            return true;
        }
        boolean hasChanges = false;
        for (var element : instance) {
            if (element != null && element.detectChange()) {
                element.markAsChanged();
                hasChanges = true;
            }
        }
        return hasChanges;
    }

    @Override
    protected void writeBuffer(@NotNull LogicalSide side, IDataSerializable @NotNull [] instance, @NotNull FriendlyByteBuf data, boolean force) {
        for (var element : instance) {
            if (element == null || !element.isChanged()) {
                data.writeBoolean(false);
            } else {
                data.writeBoolean(true);
                element.writeBuf(side, data);
            }
        }
    }

    @Override
    protected void readBuffer(@NotNull LogicalSide side, IDataSerializable @NotNull [] instance, @NotNull FriendlyByteBuf data) {
        for (var element : instance) {
            if (data.readBoolean()) {
                if (element != null) element.readBuf(side, data);
            }
        }
    }

    @Override
    protected @NotNull Data writeData(IDataSerializable @NotNull [] instance) {
        var list = new ListData();
        for (var element : instance) {
            if (element != null) {
                list.add(element.writeData());
            } else {
                list.addNull();
            }
        }
        return list;
    }

    @Override
    protected void readData(IDataSerializable @NotNull [] instance, @NotNull Data data, int dataVersion) {
        var list = data.getList();
        var length = Math.min(list.size(), instance.length);
        if (dataVersion == -1) {
            for (int i = 0; i < length; i++) {
                if (list.get(i) instanceof MapData(Map<String, Data> map) && !map.isEmpty()) {
                    var element = instance[i];
                    if (element != null) element.readData(map.get("p"), dataVersion);
                }
            }
        } else {
            for (int i = 0; i < length; i++) {
                var d = list.get(i);
                if (d != NullData.INSTANCE) {
                    var element = instance[i];
                    if (element != null) element.readData(d, dataVersion);
                }
            }
        }
    }
}
