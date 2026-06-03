package com.gregtechceu.gtceu.datasynclib;

import com.gregtechceu.gtceu.api.misc.IContentChange;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;

import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.NullData;
import com.gto.datasynclib.field.access.AbstractFieldAccess;
import com.gto.datasynclib.util.DataCodecs;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class TagSerializableAccess extends AbstractFieldAccess<ITagSerializable> {

    private boolean added;

    public TagSerializableAccess(DataFieldDefinition<ITagSerializable> definition) {
        super(definition);
    }

    private void addAware(Object source) {
        if (added) return;
        added = true;
        if (definition.isSyncToClient || definition.isSyncToServer) {
            if (getInstance(source) instanceof IContentChangeAware changeAware) {
                var run = changeAware.getOnContentsChanged();
                if (changeAware instanceof IContentChange change && change.isFreezeChanged()) {
                    if (run == null) {
                        change.setOnContentsChangedAndfreeze(() -> syncChange = true);
                    } else {
                        change.setOnContentsChangedAndfreeze(() -> {
                            run.run();
                            syncChange = true;
                        });
                    }
                } else {
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
    protected boolean hasChange(@NotNull LogicalSide side, @NotNull ITagSerializable instance, boolean auto) {
        return false;
    }

    @Override
    protected void writeBuffer(@NotNull LogicalSide side, @NotNull ITagSerializable instance, @NotNull FriendlyByteBuf data, boolean force) {
        var nbt = instance.serializeNBT();
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

    @Override
    protected void readBuffer(@NotNull LogicalSide side, @NotNull ITagSerializable instance, @NotNull FriendlyByteBuf data) {
        var type = TagTypes.getType(data.readByte());
        if (type == EndTag.TYPE) return;
        try {
            var nbt = type.load(new ByteBufInputStream(data), 0, NbtAccounter.UNLIMITED);
            instance.deserializeNBT(nbt);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected @NotNull Data writeData(@NotNull ITagSerializable instance) {
        var nbt = instance.serializeNBT();
        if (nbt == null) {
            return NullData.INSTANCE;
        } else {
            return DataCodecs.TAG_CODEC.encode(instance.serializeNBT());
        }
    }

    @Override
    protected void readData(@NotNull ITagSerializable instance, @NotNull Data data, int dataVersion) {
        if (data.isNull()) return;
        var nbt = DataCodecs.TAG_CODEC.decode(data, dataVersion);
        instance.deserializeNBT(nbt);
    }
}
