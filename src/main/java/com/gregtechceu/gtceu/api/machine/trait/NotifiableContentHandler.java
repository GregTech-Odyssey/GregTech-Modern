package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.IO;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.recipesearch.IntLongMap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public abstract class NotifiableContentHandler extends NotifiableRecipeHandlerTrait {

    @Getter
    public final IO handlerIO;

    protected boolean isEmpty;
    protected boolean emptyChanged = true;
    protected boolean searchMapChanged = true;
    private int searchGeneration;
    private int searchMapVersion;

    protected boolean isAvailable = true;

    /**
     * 该仓是否参与配方处理。默认开启；由仓界面上的按钮或覆盖板实时改写，关闭后配方检索、匹配、扣料与产出都会跳过它，
     * 但仓本身仍然照常收发（自动输入输出、管道/ME 访问都不受影响）。
     */
    @SaveToDisk(defaultValue = "true")
    protected boolean isActive = true;

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
        int generation = GTRecipeType.searchGeneration();
        if (searchMapChanged || searchGeneration != generation) {
            searchMapChanged = false;
            searchGeneration = generation;
            searchMap.clear();
            fillSearchMap(type, searchMap);
            searchMapVersion++;
        }
        return searchMap;
    }

    public final int searchMapVersion() {
        return searchMapVersion;
    }

    @Override
    public boolean isAvailable() {
        return this.isAvailable;
    }

    @Override
    public boolean isActive() {
        return this.isActive;
    }

    public void setActive(boolean active) {
        if (this.isActive == active) return;
        this.isActive = active;
        notifyListeners();
    }
}
