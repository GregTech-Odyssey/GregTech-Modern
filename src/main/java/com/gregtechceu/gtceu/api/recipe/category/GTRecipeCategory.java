package com.gregtechceu.gtceu.api.recipe.category;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GTRecipeCategory {

    // Placeholder category used if category isn't defined for a recipe for registration
    public static final GTRecipeCategory DEFAULT = new GTRecipeCategory("default", GTRecipeTypes.DUMMY_RECIPES);
    public final ResourceLocation registryKey;
    public final String name;
    private final GTRecipeType recipeType;
    private final String languageKey;
    @Nullable
    private IGuiTexture icon = null;
    private boolean isXEIVisible = true;

    public GTRecipeCategory(@NotNull GTRecipeType recipeType) {
        this.recipeType = recipeType;
        this.name = recipeType.registryName.getPath();
        this.registryKey = recipeType.registryName;
        this.languageKey = recipeType.registryName.toLanguageKey();
    }

    public GTRecipeCategory(@NotNull String categoryName, @NotNull GTRecipeType recipeType) {
        this.recipeType = recipeType;
        this.name = categoryName;
        this.registryKey = GTCEu.id(categoryName);
        this.languageKey = "%s.recipe.category.%s".formatted(GTCEu.MOD_ID, categoryName);
    }

    public static GTRecipeCategory registerDefault(@NotNull GTRecipeType recipeType) {
        GTRecipeCategory category = new GTRecipeCategory(recipeType);
        GTRegistries.RECIPE_CATEGORIES.register(category.registryKey, category);
        return category;
    }

    public IGuiTexture getIcon() {
        if (icon == null) {
            if (recipeType.getIconSupplier() != null) icon = new ItemStackTexture(recipeType.getIconSupplier().get());
            else icon = new ItemStackTexture(Items.BARRIER);
        }
        return icon;
    }

    public void addRecipe(GTRecipe recipe) {
        recipeType.addToCategoryMap(this, recipe);
    }

    public boolean shouldRegisterDisplays() {
        return (isXEIVisible || GTCEu.isDev()) && this != GTRecipeTypes.FURNACE_RECIPES.getCategory();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GTRecipeCategory that)) return false;
        return this.registryKey.equals(that.registryKey);
    }

    @Override
    public int hashCode() {
        return registryKey.hashCode();
    }

    @Override
    public String toString() {
        return "GTRecipeCategory{%s}".formatted(name);
    }

    public GTRecipeType getRecipeType() {
        return this.recipeType;
    }

    public String getLanguageKey() {
        return this.languageKey;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeCategory setIcon(@Nullable final IGuiTexture icon) {
        this.icon = icon;
        return this;
    }

    public boolean isXEIVisible() {
        return this.isXEIVisible;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeCategory setXEIVisible(final boolean isXEIVisible) {
        this.isXEIVisible = isXEIVisible;
        return this;
    }
}
