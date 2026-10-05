package com.gregtechceu.gtceu.core;

import net.minecraft.world.level.block.entity.BlockEntity;

import com.gto.datasynclib.datastream.DataComponentKey;
import com.gto.datasynclib.datastream.DataComponentMap;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public interface IBlockEntity {

    @Nullable
    DataComponentMap gtceu$getData();

    DataComponentMap gtceu$getOrCreateData();

    @Nullable
    static <T> T getData(BlockEntity blockEntity, DataComponentKey<T> key) {
        var data = ((IBlockEntity) blockEntity).gtceu$getData();
        return data == null ? null : data.getData(key);
    }

    static <T> T getOrCreateData(BlockEntity blockEntity, DataComponentKey<T> key, Supplier<T> creator) {
        return ((IBlockEntity) blockEntity).gtceu$getOrCreateData().getOrCreateData(key, creator);
    }
}
