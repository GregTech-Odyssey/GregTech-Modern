package com.gregtechceu.gtceu.core;

import com.gregtechceu.gtceu.api.pattern.MultiblockWorldData;
import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ILevel {

    MultiblockWorldData gtceu$getMultiblockWorldSavedData();

    void gtceu$setMultiblockWorldSavedData(MultiblockWorldData data);

    @NotNull
    List<TaskHandler.RunnableEntry> gtceu$getTasks();

    LongOpenHashSet gtceu$getHighlightCache();

    static List<TaskHandler.RunnableEntry> getTasks(@NotNull Level level) {
        return ((ILevel) level).gtceu$getTasks();
    }

    static LongSet getHighlightCache(@Nullable Level level) {
        if (level == null) return LongSets.emptySet();
        return ((ILevel) level).gtceu$getHighlightCache();
    }
}
