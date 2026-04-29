package com.gregtechceu.gtceu.common.recipe;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.FacadeItemBehaviour;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class FacadeCoverRecipe extends CustomRecipe {

    public static final RecipeSerializer<FacadeCoverRecipe> SERIALIZER = new SimpleCraftingRecipeSerializer<>(
            FacadeCoverRecipe::new);

    public FacadeCoverRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int plateSize = 0;
        int facadeSize = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            var item = container.getItem(i);
            if (item.isEmpty()) continue;
            if (FacadeItemBehaviour.isValidFacade(item)) {
                facadeSize++;
                if (facadeSize > 1) {
                    return false;
                }
                continue;
            }
            if (item.is(ChemicalHelper.getTag(TagPrefix.plate, GTMaterials.Iron))) {
                plateSize++;
                continue;
            }
            return false;
        }
        return facadeSize == 1 && plateSize == 3;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryManager) {
        ItemStack facadeStack = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            var item = container.getItem(i);
            if (item.isEmpty()) continue;
            if (FacadeItemBehaviour.isValidFacade(item)) {
                if (!facadeStack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                facadeStack = item;
            }
        }
        if (facadeStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack itemStack = GTItems.COVER_FACADE.asStack();
        FacadeItemBehaviour.setFacadeStack(itemStack, facadeStack);
        itemStack.setCount(6);
        return itemStack;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 4;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryManager) {
        var result = GTItems.COVER_FACADE.asStack();
        FacadeItemBehaviour.setFacadeStack(result, new ItemStack(Blocks.STONE));
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
