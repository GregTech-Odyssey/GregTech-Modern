package com.gregtechceu.gtceu.api.capability;

import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.issue.GTIssues;
import com.gregtechceu.gtceu.api.machine.trait.EnchantmentSlotHandler;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.EURecipeInfo;
import com.gregtechceu.gtceu.common.machine.trait.miner.MinerLogic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IMiner extends IRecipeLogicMachine, IMachineLife {

    @Override
    @NotNull
    MinerLogic getRecipeLogic();

    @Override
    default void onMachineRemoved() {
        getRecipeLogic().onRemove();
    }

    boolean drainInput(boolean simulate);

    default void reportDrainIssue() {
        reportIssue(GTIssues.EU_SHORT, null, IO.IN, EURecipeInfo.INSTANCE, -1, -1, -1, null);
    }

    /**
     * 本矿机的附魔槽。
     *
     * <p>
     * 返回 {@code null} 表示这台矿机没有附魔槽，此时时运 / 效率不会生效。
     * 有附魔槽的实现应保证机器创建时就挂上它，不要中途更换实例。
     */
    @Nullable
    default EnchantmentSlotHandler getEnchantmentSlot() {
        return null;
    }

    static int getWorkingArea(int maximumRadius) {
        return maximumRadius * 2 + 1;
    }
}
