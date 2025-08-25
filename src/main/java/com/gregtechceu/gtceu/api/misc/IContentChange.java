package com.gregtechceu.gtceu.api.misc;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;

import org.jetbrains.annotations.NotNull;

public interface IContentChange extends IContentChangeAware {

    boolean isFreezeChanged();

    void setOnContentsChangedAndfreeze(@NotNull Runnable onContentsChanged);
}
