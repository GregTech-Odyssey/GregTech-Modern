package com.gregtechceu.gtceu.datasynclib;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;

import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.TagTypes;
import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datastream.codec.ValueOps;
import com.gto.datasynclib.field.access.AbstractFieldAccess;
import com.gto.datasynclib.util.ValueCodecs;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;

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
                        changeAware.setOnContentsChanged(() -> changed = true);
                    } else {
                        changeAware.setOnContentsChanged(() -> {
                            run.run();
                            changed = true;
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
        if (definition.skipSync(side, source, instance)) return false;
        if (this.instance != instance) {
            this.instance = instance;
            return changed = true;
        }
        return changed;
    }

    @Override
    protected boolean hasChange(@NotNull LogicalSide side, @NotNull ITagSerializable @NotNull [] instance, boolean auto) {
        return false;
    }

    @Override
    protected void doWriteBuffer(@NotNull LogicalSide side, ITagSerializable @NotNull [] instance, @NotNull FriendlyByteBuf data, boolean force) {
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
    protected void doReadBuffer(@NotNull LogicalSide side, ITagSerializable @NotNull [] instance, @NotNull FriendlyByteBuf data) {
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
    protected @NotNull Object doWriteValue(@NotNull Object source, ITagSerializable @NotNull [] instance, @NotNull ValueOps ops) {
        var list = new ArrayList<>(instance.length);
        for (var element : instance) {
            var nbt = element == null ? null : element.serializeNBT();
            if (nbt == null) {
                list.add(ops.createNull());
            } else {
                list.add(ValueCodecs.TAG.encode(ops, nbt));
            }
        }
        return ops.createList(list);
    }

    @Override
    protected void doReadValue(ITagSerializable @NotNull [] instance, @NotNull Object data, @NotNull ValueOps ops) {
        var list = ops.getList(data);
        var length = Math.min(list.size(), instance.length);
            for (int i = 0; i < length; i++) {
                var d = list.get(i);
                if (!ops.isNull(d)) {
                    var element = instance[i];
                    if (element != null) {
                        var nbt = ValueCodecs.TAG.decode(ops, d);
                        element.deserializeNBT(nbt);
                    }
                }
            }
        }
}
