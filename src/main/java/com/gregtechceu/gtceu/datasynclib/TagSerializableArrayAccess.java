package com.gregtechceu.gtceu.datasynclib;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;

import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.TagTypes;
import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.*;
import com.gto.datasynclib.field.access.AbstractFieldAccess;
import com.gto.datasynclib.util.DataCodecs;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

public final class TagSerializableArrayAccess extends AbstractFieldAccess<ITagSerializable[]> {

    private boolean added;

    public TagSerializableArrayAccess(DataFieldDefinition<ITagSerializable[]> definition) {
        super(definition);
    }

    private void addAware(Object source) {
        if (added) return;
        added = true;
        if (definition.isSyncToClient || definition.isSyncToServer) {
            for (var element : getInstance(source)) {
                if (element instanceof IContentChangeAware changeAware) {
                    var run = changeAware.getOnContentsChanged();
                    if (run == null) {
                        changeAware.setOnContentsChanged(() -> syncChange = true);
                    } else {
                        changeAware.setOnContentsChanged(() -> {
                            run.run();
                            syncChange = true;
                        });
                    }
                }
            }
        }
    }

    @Override
    public boolean detectChange(@NotNull LogicalSide side, @NotNull Object source, boolean auto) {
        addAware(source);
        var instance = getInstance(source);
        if (this.instance != instance) {
            this.instance = instance;
            return syncChange = true;
        }
        return syncChange;
    }

    @Override
    protected boolean hasChange(@NotNull LogicalSide side, @NotNull ITagSerializable @NotNull [] instance, boolean auto) {
        return false;
    }

    @Override
    protected void writeBuffer(@NotNull LogicalSide side, ITagSerializable @NotNull [] instance, @NotNull FriendlyByteBuf data, boolean force) {
        for (var element : instance) {
            var nbt = element == null ? null : element.serializeNBT();
            if (nbt == null) {
                data.writeByte(0);
            } else {
                data.writeByte(nbt.getId());
                try {
                    nbt.write(new ByteBufOutputStream(data));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    protected void readBuffer(@NotNull LogicalSide side, ITagSerializable @NotNull [] instance, @NotNull FriendlyByteBuf data) {
        for (var element : instance) {
            var type = TagTypes.getType(data.readByte());
            if (type == EndTag.TYPE) return;
            try {
                var nbt = type.load(new ByteBufInputStream(data), 0, NbtAccounter.UNLIMITED);
                if (element != null) element.deserializeNBT(nbt);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected @NotNull Data writeData(ITagSerializable @NotNull [] instance) {
        var list = new ListData();
        for (var element : instance) {
            var nbt = element == null ? null : element.serializeNBT();
            if (nbt == null) {
                list.addNull();
            } else {
                list.add(DataCodecs.TAG_CODEC.encode(nbt));
            }
        }
        return list;
    }

    @Override
    protected void readData(ITagSerializable @NotNull [] instance, @NotNull Data data, int dataVersion) {
        var list = data.getList();
        var length = Math.min(list.size(), instance.length);
        if (dataVersion == -1) {
            for (int i = 0; i < length; i++) {
                if (list.get(i) instanceof MapData(Map<String, Data> map) && !map.isEmpty()) {
                    var element = instance[i];
                    if (element != null) {
                        var nbt = DataCodecs.TAG_CODEC.decode(map.get("p"), dataVersion);
                        element.deserializeNBT(nbt);
                    }
                }
            }
        } else {
            for (int i = 0; i < length; i++) {
                var d = list.get(i);
                if (d != NullData.INSTANCE) {
                    var element = instance[i];
                    if (element != null) {
                        var nbt = DataCodecs.TAG_CODEC.decode(d, dataVersion);
                        element.deserializeNBT(nbt);
                    }
                }
            }
        }
    }
}
