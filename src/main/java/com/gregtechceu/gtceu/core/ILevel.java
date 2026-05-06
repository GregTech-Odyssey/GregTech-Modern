package com.gregtechceu.gtceu.core;

import com.gregtechceu.gtceu.api.pattern.MultiblockWorldData;
import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.world.level.Level;

import com.gto.datasynclib.datasream.DataComponentKey;
import com.gto.datasynclib.datasream.DataComponentMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ILevel {

    DataComponentKey<LongSet> HIGHLIGHTCACHEKEY = DataComponentKey.createNoCodec("highlightcache");

    DataComponentMap gtceu$getCapabilities();

    @NotNull
    TaskHandler gtceu$getTaskHandler();

    @NotNull
    TaskHandler gtceu$getAsyncTaskHandler();

    void gtceu$clear();

    MultiblockWorldData gtceu$getMultiblockWorldSavedData();

    void gtceu$setMultiblockWorldSavedData(MultiblockWorldData data);

    static <T> T getCapability(@NotNull Level level, DataComponentKey<T> key) {
        return ((ILevel) level).gtceu$getCapabilities().getData(key);
    }

    static <T> void setCapability(@NotNull Level level, DataComponentKey<T> key, T capability) {
        ((ILevel) level).gtceu$getCapabilities().put(key, capability);
    }

    static LongSet getHighlightCache(@Nullable Level level) {
        if (level == null) return LongSets.emptySet();
        LongSet cache = getCapability(level, HIGHLIGHTCACHEKEY);
        if (cache == null) {
            cache = new LongOpenHashSet();
            setCapability(level, HIGHLIGHTCACHEKEY, cache);
        }
        return cache;
    }
}
