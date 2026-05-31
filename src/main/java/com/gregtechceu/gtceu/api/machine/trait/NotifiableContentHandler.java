package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.IO;

import com.fast.recipesearch.IntLongMap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public abstract class NotifiableContentHandler extends NotifiableRecipeHandlerTrait {

    @Getter
    public final IO handlerIO;

    protected boolean isEmpty;
    protected boolean emptyChanged;
    protected boolean searchMapChanged = true;

    protected boolean isAvailable = true;

    protected final IntLongMap searchMap = new IntLongMap();

    protected NotifiableContentHandler(MetaMachine machine, IO handlerIO) {
        super(machine);
        this.handlerIO = handlerIO;
    }

    protected abstract boolean updateEmpty();

    protected abstract void fillSearchMap(@NotNull GTRecipeType type, @NotNull IntLongMap map);

    public void onContentsChanged() {
        emptyChanged = true;
        searchMapChanged = true;
        machine.onChanged();
        notifyListeners();
    }

    public final boolean isEmpty() {
        if (emptyChanged) {
            emptyChanged = false;
            isEmpty = updateEmpty();
        }
        return isEmpty;
    }

    @Override
    public final IntLongMap getSearchMap(@NotNull GTRecipeType type) {
        if (searchMapChanged) {
            searchMapChanged = false;
            searchMap.clear();
            fillSearchMap(type, searchMap);
        }
        return searchMap;
    }

    @Override
    public boolean isAvailable() {
        return this.isAvailable;
    }
}
