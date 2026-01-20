package com.gregtechceu.gtceu.core;

import com.gregtechceu.gtceu.api.pattern.MultiblockWorldData;
import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface ILevel {

    Map<Class<?>, Object> gtceu$getCapabilities();

    @NotNull
    TaskHandler gtceu$getTaskHandler();

    @NotNull
    TaskHandler gtceu$getAsyncTaskHandler();

    MultiblockWorldData gtceu$getMultiblockWorldSavedData();

    void gtceu$setMultiblockWorldSavedData(MultiblockWorldData data);

    static <T> T getCapability(@NotNull Level level, Class<?> clazz) {
        return (T) ((ILevel) level).gtceu$getCapabilities().get(clazz);
    }

    static void setCapability(@NotNull Level level, Class<?> clazz, Object capability) {
        ((ILevel) level).gtceu$getCapabilities().put(clazz, capability);
    }

    static LongSet getHighlightCache(@Nullable Level level) {
        if (level == null) return LongSets.emptySet();
        LongSet cache = getCapability(level, LongSet.class);
        if (cache == null) {
            cache = new LongOpenHashSet();
            setCapability(level, LongSet.class, cache);
        }
        return cache;
    }
}
