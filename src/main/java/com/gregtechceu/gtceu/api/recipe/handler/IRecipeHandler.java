package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyVisitor;
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

    /**
     * 该处理器当前是否参与配方。默认参与；关闭后它的存储会被检索、匹配与提交整条链路<b>实时</b>跳过
     * （不参与检索、不扣料、不接产物），因此可以在工作时随时开关，无需重建任何处理器分组。
     * <p>
     * 「是否挂载」看 {@link #isAvailable()}，「是否激活」看这里：前者在机器创建时就固定，后者可以随界面按钮或
     * 覆盖板变化，见 {@link com.gregtechceu.gtceu.api.machine.trait.NotifiableContentHandler#setActive(boolean)}。
     */
    default boolean isActive() {
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
}
