package com.gregtechceu.gtceu.uiwidgets.recipe;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.ui.RecipeInfoBuilder;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.integration.xei.handlers.item.CycleItemStackHandler;
import com.gregtechceu.gtceu.uipro.elements.FluidSlot;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;

import com.lowdragmc.lowdraglib.jei.IngredientIO;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * 配方页里只作展示的槽（配方要求的线圈、维度、研究数据、邻接流体……）：标准物品槽 / 流体槽，不可取放，
 * 在配方查看器里按 {@code io} 当作输入、催化剂等参与查配方、查用途。
 */
public final class RecipeDisplaySlots {

    private RecipeDisplaySlots() {}

    /** 轮流显示 {@code stacks} 的物品槽。 */
    public static ItemSlot item(List<ItemStack> stacks, IngredientIO io) {
        var slot = new ItemSlot(new CycleItemStackHandler(List.of(stacks)), 0, false, false);
        slot.setIngredientIO(io);
        return slot;
    }

    /** 显示一个物品的槽。 */
    public static ItemSlot item(ItemStack stack, IngredientIO io) {
        return item(List.of(stack), io);
    }

    /** 显示一种流体的槽（不显示数量）。 */
    public static FluidSlot fluid(FluidStack stack, IngredientIO io) {
        var slot = new FluidSlot(new CustomFluidTank(stack), 0, false, false);
        slot.setShowAmount(false);
        slot.setIngredientIO(io);
        return slot;
    }

    /** 需要加热线圈的配方（EBF 等）：能达到炉温的线圈轮流显示，作为配方页的额外展示槽。 */
    public static void coilInfo(GTRecipeDefinition recipe, RecipeInfoBuilder info) {
        int temperature = recipe.data.getInt(GTRecipeDataKeys.EBF_TEMP);
        info.slot(() -> item(coils(coilTemperature -> coilTemperature >= temperature), IngredientIO.CATALYST));
    }

    /** 线圈温度满足 {@code accepts} 的所有加热线圈。 */
    public static List<ItemStack> coils(IntPredicate accepts) {
        var coils = new ArrayList<ItemStack>();
        for (var coil : GTCEuAPI.HEATING_COILS.entrySet()) {
            if (accepts.test(coil.getKey().getCoilTemperature())) coils.add(new ItemStack(coil.getValue().get()));
        }
        return coils;
    }
}
