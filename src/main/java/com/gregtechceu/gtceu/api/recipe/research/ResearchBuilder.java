package com.gregtechceu.gtceu.api.recipe.research;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;
import com.gregtechceu.gtceu.utils.GTStringUtils;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import org.jetbrains.annotations.NotNull;

public abstract class ResearchBuilder<T extends ResearchBuilder<T>> {

    protected ItemStack researchStack;
    protected Item dataItem;
    protected String researchId;
    protected long eut;
    protected int duration;

    public T researchStack(@NotNull ItemLike researchStack) {
        return researchStack(researchStack.asItem().getDefaultInstance());
    }

    public T researchStack(@NotNull ItemStack researchStack) {
        if (!researchStack.isEmpty()) {
            this.researchStack = researchStack;
        }
        return (T) this;
    }

    public T dataStack(@NotNull ItemLike dataItem) {
        this.dataItem = dataItem.asItem();
        return (T) this;
    }

    public T dataStack(@NotNull ItemStack dataItem) {
        this.dataItem = dataItem.getItem();
        return (T) this;
    }

    public T researchId(String researchId) {
        this.researchId = researchId;
        return (T) this;
    }

    public T EUt(long eut) {
        this.eut = eut;
        return (T) this;
    }

    public T duration(int duration) {
        this.duration = duration;
        return (T) this;
    }

    protected void validateResearchItem() {
        if (researchStack == null) {
            throw new IllegalArgumentException("Research stack cannot be null or empty!");
        }
        if (researchId == null) {
            researchId = GTStringUtils.itemStackToString(researchStack);
        }
        if (dataItem == null) {
            dataItem = getDefaultDataItem();
        }
    }

    protected abstract Item getDefaultDataItem();

    public abstract ResearchCondition build(GTRecipeType recipeType);
}
