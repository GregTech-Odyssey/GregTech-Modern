package com.gregtechceu.gtceu.api.recipe.info;

import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.client.TooltipsHandler;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidEntryList;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidStackList;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidTagList;
import com.gregtechceu.gtceu.integration.xei.handlers.fluid.CycleFluidEntryHandler;
import com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget;

import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 流体内容种类的元信息与渲染实现。
 *
 * <p>
 * 与 {@link ItemRecipeInfo} 对称：把配方里的 {@link FluidIngredient} 转成配方查看器条目
 * （单个流体堆或流体标签列表），并绑定到流体罐控件上。
 */
public final class FluidRecipeInfo extends ContentRecipeInfo<FluidStack, FluidIngredient> {

    /** 全局唯一实例，注册名为 {@code fluid}。 */
    public final static FluidRecipeInfo INSTANCE = new FluidRecipeInfo();

    private FluidRecipeInfo() {
        super("fluid", 0xFF3C70EE, true, 1);
    }

    /** 把流体内容转成查看器条目，并按配方类型的最大输出数补 {@code null} 占位。 */
    @Override
    public @NotNull List<Object> createXEIContainerContents(List<Content<FluidIngredient>> contents, GTRecipeDefinition recipe, IO io) {
        List<Object> entryLists = contents.stream()
                .map(FluidRecipeInfo::mapFluid)
                .collect(Collectors.toList());

        while (entryLists.size() < recipe.recipeType.getMaxOutputs(this)) entryLists.add(null);
        return entryLists;
    }

    /** 把条目列表包成可循环切换的流体显示 handler。 */
    @SuppressWarnings("unchecked") // cast is safe if you don't pass the wrong thing.
    public Object createXEIContainer(List<?> contents) {
        return new CycleFluidEntryHandler((List<FluidEntryList>) contents);
    }

    /** 流体罐控件，填充方向设为 {@code ALWAYS_FULL}，该设置同时用于机器界面与配方查看器。 */
    @NotNull
    @Override
    public Widget createWidget() {
        TankWidget tank = new TankWidget();
        tank.initTemplate();
        tank.setFillDirection(ProgressTexture.FillDirection.ALWAYS_FULL);
        return tank;
    }

    /** 该种类使用 {@link TankWidget} 显示。 */
    @NotNull
    @Override
    public Class<? extends Widget> getWidgetClass() {
        return TankWidget.class;
    }

    /**
     * 把槽位信息应用到流体罐上：绑定流体存储、设置输入 / 输出类型与是否可交互；
     * 在非查看器界面（{@code !isXEI}）下还会把该流体的提示文本追加到槽位 tooltip 上。
     */
    @Override
    public void applyWidgetInfo(@NotNull Widget widget,
                                int index,
                                boolean isXEI,
                                IO io,
                                GTRecipeTypeUI.@UnknownNullability("null when storage == null") RecipeHolder recipeHolder,
                                @NotNull GTRecipeType recipeType,
                                @UnknownNullability("null when content == null") GTRecipeDefinition recipe,
                                @Nullable Content<FluidIngredient> content,
                                @Nullable Object storage, int recipeTier, int chanceTier) {
        if (widget instanceof TankWidget tank) {
            if (storage instanceof IFluidHandler fluidHandler) {
                tank.setFluidTank(fluidHandler, index);
            }
            tank.setIngredientIO(io == IO.IN ? IngredientIO.INPUT : IngredientIO.OUTPUT);
            tank.setAllowClickFilled(!isXEI);
            tank.setAllowClickDrained(!isXEI && io.support(IO.IN));
            if (isXEI) tank.setShowAmount(false);
            if (content != null) {
                float chance = (float) recipe.chanceFunction
                        .getBoostedChance(content, recipeTier, chanceTier) / Content.MAX_CHANCE;
                tank.setXEIChance(chance);
                tank.setOnAddedTooltips((w, tooltips) -> {
                    FluidIngredient ingredient = content.inner;
                    if (!isXEI && !ingredient.getFluidStack().isEmpty()) {
                        TooltipsHandler.appendFluidTooltips(ingredient.getFluidStack(), tooltips::add, TooltipFlag.NORMAL);
                    }
                    GTRecipeWidget.setConsumedChance(content, ChanceLogic.OR, tooltips, recipeTier, chanceTier, recipe.chanceFunction);
                });
                if (io == IO.IN && (content.chance == 0)) {
                    tank.setIngredientIO(IngredientIO.CATALYST);
                }
            }
        }
    }

    /**
     * 把单条流体内容映射成查看器条目：具体流体映射成流体堆列表，标签则映射成流体标签列表。
     *
     * <p>
     * Maps fluids to a FluidEntryList for XEI: either a FluidTagList or a FluidStackList
     */
    @SuppressWarnings("unchecked") // FluidIngredient only ever stores fluid tag keys
    public static FluidEntryList mapFluid(Content<FluidIngredient> ingredient) {
        int amount = ingredient.inner.getAmount();
        CompoundTag nbt = ingredient.inner.nbt;
        if (ingredient.inner.value instanceof Fluid fluid) {
            return FluidStackList.of(new FluidStack(fluid, amount, nbt));
        } else if (ingredient.inner.value instanceof TagKey<?> tag) {
            return FluidTagList.of((TagKey<Fluid>) tag, amount, nbt);
        }
        return new FluidStackList();
    }
}
