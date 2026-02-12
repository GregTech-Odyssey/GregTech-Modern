package com.gregtechceu.gtceu.utils;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.component.IDataItem;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.transfer.item.ItemHandlerList;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ResearchManager {

    public static Int2ObjectFunction<Item> DATA_ITEM_PROVIDER = cwut -> {
        if (cwut > 32) {
            return GTItems.TOOL_DATA_MODULE.get();
        } else {
            return GTItems.TOOL_DATA_ORB.get();
        }
    };

    public static final String RESEARCH_NBT_TAG = "assembly_line_research";
    public static final String RESEARCH_ID_NBT_TAG = "research_id";
    public static final String RESEARCH_TYPE_NBT_TAG = "research_type";

    @NotNull
    public static ItemStack getDefaultResearchStationItem(int cwut) {
        return DATA_ITEM_PROVIDER.apply(cwut).getDefaultInstance();
    }

    private ResearchManager() {}

    /**
     * @param stackCompound the compound contained on the ItemStack to write to
     * @param researchId    the research id
     */
    public static void writeResearchToNBT(@NotNull CompoundTag stackCompound, @NotNull String researchId,
                                          GTRecipeType recipeType) {
        CompoundTag compound = new CompoundTag();
        compound.putString(RESEARCH_ID_NBT_TAG, researchId);
        compound.putString(RESEARCH_TYPE_NBT_TAG, recipeType.registryName.toString());
        stackCompound.put(RESEARCH_NBT_TAG, compound);
    }

    /**
     * @param stack the ItemStack to read from
     * @return the research id
     */
    @Nullable
    public static ResearchItem readResearchId(@NotNull ItemStack stack) {
        CompoundTag compound = stack.getTag();
        if (!hasResearchTag(compound)) return null;

        CompoundTag researchCompound = compound.getCompound(RESEARCH_NBT_TAG);
        return ResearchItem.CODEC.parse(NbtOps.INSTANCE, researchCompound).result().orElse(null);
    }

    /**
     * @param stack      the stack to check
     * @param isDataBank if the caller is a Data Bank. Pass "true" here if your use-case does not matter for this check.
     * @return if the stack is a data item
     */
    public static boolean isStackDataItem(@NotNull ItemStack stack, boolean isDataBank) {
        if (stack.getItem() instanceof IComponentItem metaItem) {
            for (IItemComponent behaviour : metaItem.getComponents()) {
                if (behaviour instanceof IDataItem dataItem) {
                    return !dataItem.requireDataBank() || isDataBank;
                }
            }
        }
        return false;
    }

    /**
     * @param stack the stack to check
     * @return if the stack has the research CompoundTag
     */
    public static boolean hasResearchTag(@NotNull ItemStack stack) {
        return hasResearchTag(stack.getTag());
    }

    /**
     * @param compound the compound to check
     * @return if the tag has the research CompoundTag
     */
    private static boolean hasResearchTag(@Nullable CompoundTag compound) {
        if (compound == null || compound.isEmpty()) return false;
        return compound.contains(RESEARCH_NBT_TAG, Tag.TAG_COMPOUND);
    }

    public record ResearchItem(@NotNull String researchId, @NotNull GTRecipeType recipeType) {

        // spotless:off
        public static final Codec<ResearchItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("research_id").forGetter(ResearchItem::researchId),
                GTRegistries.RECIPE_TYPES.codec().fieldOf("research_type").forGetter(ResearchItem::recipeType)
        ).apply(instance, ResearchItem::new));
        // spotless:on
    }

    public static class DataStickCopyScannerLogic implements GTRecipeType.ICustomRecipeLogic {

        private static final int EUT = 2;
        private static final int DURATION = 100;

        @Override
        public GTRecipeDefinition createCustomRecipe(IRecipeCapabilityHolder holder) {
            var itemInputs = holder.getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).stream()
                    .filter(IItemHandlerModifiable.class::isInstance)
                    .map(IItemHandlerModifiable.class::cast)
                    .toArray(IItemHandlerModifiable[]::new);
            var inputs = new ItemHandlerList(itemInputs);
            if (inputs.getSlots() > 1) {
                // try the data recipe both ways, prioritizing overwriting the first
                GTRecipeDefinition recipe = createDataRecipe(inputs.getStackInSlot(0), inputs.getStackInSlot(1));
                if (recipe != null) return recipe;

                return createDataRecipe(inputs.getStackInSlot(1), inputs.getStackInSlot(0));
            }
            return null;
        }

        private GTRecipeDefinition createDataRecipe(@NotNull ItemStack first, @NotNull ItemStack second) {
            CompoundTag compound = second.getTag();
            if (compound == null) return null;

            // Both must be data items
            if (!isStackDataItem(first, true)) return null;
            if (!isStackDataItem(second, true)) return null;

            ItemStack output = first.copy();
            output.setTag(compound.copy());
            return GTRecipeTypes.SCANNER_RECIPES.recipeBuilder(GTStringUtils.itemStackToString(output))
                    .inputItems(first)
                    .notConsumable(second)
                    .outputItems(output)
                    .duration(DURATION).EUt(EUT)
                    .build();
        }

        @Override
        public void buildRepresentativeRecipes() {
            ItemStack copiedStick = GTItems.TOOL_DATA_STICK.asStack();
            copiedStick.setHoverName(Component.translatable("gtceu.scanner.copy_stick_from"));
            ItemStack emptyStick = GTItems.TOOL_DATA_STICK.asStack();
            emptyStick.setHoverName(Component.translatable("gtceu.scanner.copy_stick_empty"));
            ItemStack resultStick = GTItems.TOOL_DATA_STICK.asStack();
            resultStick.setHoverName(Component.translatable("gtceu.scanner.copy_stick_to"));

            GTRecipeDefinition recipe = GTRecipeTypes.SCANNER_RECIPES
                    .recipeBuilder("copy_" + GTStringUtils.itemStackToString(copiedStick))
                    .inputItems(emptyStick)
                    .notConsumable(copiedStick)
                    .outputItems(resultStick)
                    .duration(DURATION).EUt(EUT)
                    .build();

            GTRecipeTypes.SCANNER_RECIPES.addToMainCategory(recipe);
        }
    }
}
