package com.gregtechceu.gtceu.core;

import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ILevel {

    @NotNull
    List<TaskHandler.RunnableEntry> gtceu$getTasks();

    static List<TaskHandler.RunnableEntry> getTasks(@NotNull Level level) {
        return ((ILevel) level).gtceu$getTasks();
    }
}
