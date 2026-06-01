package com.gto.datasynclib.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import com.gto.datasynclib.datasream.codec.DataDecoder;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.IntArrayData;
import com.gto.datasynclib.datasream.data.MapData;
import com.gto.datasynclib.datasream.data.NullData;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.UUID;

@UtilityClass
public class DataFixer {

    public UUID decodeUUID(@NotNull Data data, int dataVersion) {
        if (dataVersion == -1 && data instanceof IntArrayData) {
            return UUIDUtil.uuidFromIntArray(data.getIntArray());
        }
        return data.getUUID();
    }

    public BlockPos decodeBlockPos(@NotNull Data data) {
        if (data instanceof MapData map) {
            return new BlockPos(map.getInt("X"), map.getInt("Y"), map.getInt("Z"));
        }
        var array = data.getIntArray();
        return new BlockPos(array[0], array[1], array[2]);
    }

    public <T> T[] decodearray(Class<T> type, DataDecoder<T> decoder, @NotNull Data data, int dataVersion) {
        var list = data.getList();
        var size = list.size();
        var array = (Object[]) Array.newInstance(type, size);
        for (int i = 0; i < size; i++) {
            array[i] = decodearray(decoder, list.get(i), dataVersion);
        }
        return (T[]) array;
    }

    public <T> T decodearray(DataDecoder<T> decoder, @NotNull Data data, int dataVersion) {
        if (dataVersion == -1) {
            if (data instanceof MapData(Map<String, Data> map)) {
                if (map.isEmpty()) {
                    return null;
                } else {
                    return decoder.decode(map.get("p"), dataVersion);
                }
            }
            return null;
        } else {
            if (data != NullData.INSTANCE) {
                return decoder.decode(data, dataVersion);
            } else {
                return null;
            }
        }
    }
}
