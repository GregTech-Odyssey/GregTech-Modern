package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import com.gto.recipesearch.IntLongMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 机器上可参与配方的一个环节。数组型成员只需返回 {@link #storage}，规划与提交由 {@link RecipeHandlerUnit} 直接读写数组；
 * 自定义成员（网络、实体、有状态物品等）实现慢路径的预留与提交，提交可失败并须支持回退。
 */
public interface IRecipeHandler extends IFilteredHandler {

    @Nullable
    default KeyInventory<?> storage(AEKeyType type) {
        return null;
    }

    default boolean handlesItems() {
        return storage(AEKeyTypes.ITEMS) != null;
    }

    default boolean handlesFluids() {
        return storage(AEKeyTypes.FLUIDS) != null;
    }

    default long available(AEKeyType type, KeyIngredient ingredient) {
        return 0;
    }

    default long reserveInput(PlanScratch plan, int member, AEKeyType type, int entry, KeyIngredient ingredient, long need, boolean consume) {
        return 0;
    }

    default boolean commitInput(PlanScratch plan, int member, AEKeyType type) {
        return true;
    }

    default void rollbackInput(PlanScratch plan, int member, AEKeyType type) {}

    default long reserveOutput(PlanScratch plan, int member, AEKeyType type, int entry, AEKey key, long amount) {
        return 0;
    }

    default long insertOutput(AEKeyType type, AEKey key, long amount) {
        return 0;
    }

    default void onRecipeCommitted(GTRecipe recipe) {}

    default boolean forEachKey(AEKeyType type, KeyVisitor visitor) {
        var inv = storage(type);
        if (inv == null) return false;
        int size = inv.size();
        for (int i = 0; i < size; i++) {
            long amount = inv.amountAt(i);
            if (amount > 0 && visitor.visit(inv.rawKeyAt(i), amount)) return true;
        }
        return false;
    }

    default IntLongMap getSearchMap(@NotNull GTRecipeType type) {
        return IntLongMap.EMPTY;
    }

    default void addToSearchMap(@NotNull IntLongMap target, @NotNull GTRecipeType type) {
        getSearchMap(type).addTo(target);
    }

    default boolean isAvailable() {
        return true;
    }

    default boolean isNotConsumable() {
        return false;
    }

    default boolean isOnlyRecipe() {
        return false;
    }

    default boolean isPresenceOnly() {
        return false;
    }

    default boolean isLossyRollback() {
        return false;
    }

    default boolean isInfiniteCapacity(AEKeyType type) {
        return false;
    }

    default boolean isSearchable() {
        return handlesItems() || handlesFluids();
    }

    @FunctionalInterface
    interface KeyVisitor {

        boolean visit(AEKey key, long amount);
    }
}
