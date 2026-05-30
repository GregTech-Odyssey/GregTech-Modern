package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.SteamTexture;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.content.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.info.*;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.IntTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.fast.fastcollection.O2OOpenCacheHashMap;
import com.fast.fastcollection.OpenCacheHashSet;
import com.fast.recipesearch.IntLongMap;
import com.fast.recipesearch.RecipeSearcher;
import com.gto.datasynclib.datasream.DataComponentMap;
import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntSortedMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

public class GTRecipeType implements RecipeType<Recipe<?>> {

    public final ResourceLocation registryName;
    public final String group;
    public final Object2IntSortedMap<RecipeInfo> maxInputs = new Object2IntAVLTreeMap<>(RecipeInfo.COMPARATOR);
    public final Object2IntSortedMap<RecipeInfo> maxOutputs = new Object2IntAVLTreeMap<>(RecipeInfo.COMPARATOR);
    public final GTRecipeDefinition defaultDefinition;
    protected GTRecipeBuilder recipeBuilder;

    @Getter
    protected GTRecipeTypeUI recipeUI = new GTRecipeTypeUI(this);
    @Getter
    protected GTRecipeType smallRecipeMap;
    @Nullable
    protected Supplier<ItemStack> iconSupplier;
    @Nullable
    protected SoundEntry sound;
    @Getter
    protected List<Function<DataComponentMap, String>> dataInfos = new ArrayList<>();
    @Getter
    protected boolean isScanner;
    // Does this recipe type have a research item slot? If this is true you MUST create a custom UI.
    @Getter
    protected boolean hasResearchSlot;
    @Getter
    protected final Set<RecipeType<?>> proxyRecipes;
    @Getter
    protected final GTRecipeCategory category;
    @Getter
    protected final Map<GTRecipeCategory, Set<GTRecipeDefinition>> categoryMap = new O2OOpenCacheHashMap<>();
    @Getter
    protected boolean offsetVoltageText = false;
    @Getter
    protected int voltageTextOffset = 20;
    protected final Map<String, Collection<GTRecipeDefinition>> researchEntries = new O2OOpenCacheHashMap<>();
    @Getter
    protected final List<ICustomRecipeLogic> customRecipeLogicRunners = new ArrayList<>();

    @Getter
    protected boolean noSearch;

    protected RecipeDB db;

    public final Map<ResourceLocation, GTRecipeDefinition> recipes = new O2OOpenCacheHashMap<>();

    public GTRecipeType(ResourceLocation registryName, String group, RecipeType<?>... proxyRecipes) {
        this.registryName = registryName;
        this.group = group;
        this.category = GTRecipeCategory.registerDefault(this);
        recipeBuilder = new GTRecipeBuilder(registryName, this);
        // must be linked to stop json contents from shuffling
        this.proxyRecipes = new ReferenceOpenHashSet<>(proxyRecipes);
        this.defaultDefinition = new GTRecipeDefinition(false, this, category, GTCEu.id("default"), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), new DataComponentMap(), ChanceBoostFunction.OVERCLOCK, 0, 0, 100, 0);
    }

    public static boolean available(GTRecipeType recipeType, GTRecipeType... types) {
        if (recipeType == null || recipeType == GTRecipeTypes.DUMMY_RECIPES) return true;
        for (var type : types) {
            if (recipeType == type || type.proxyRecipes.contains(recipeType)) return true;
        }
        return false;
    }

    public GTRecipeType setMaxIOSize(int maxInputs, int maxOutputs, int maxFluidInputs, int maxFluidOutputs) {
        return setMaxSize(IO.IN, ItemRecipeInfo.INSTANCE, maxInputs).setMaxSize(IO.IN, FluidRecipeInfo.INSTANCE, maxFluidInputs).setMaxSize(IO.OUT, ItemRecipeInfo.INSTANCE, maxOutputs).setMaxSize(IO.OUT, FluidRecipeInfo.INSTANCE, maxFluidOutputs);
    }

    public GTRecipeType setEUIO(IO io) {
        if (io.support(IO.IN)) {
            setMaxSize(IO.IN, EURecipeInfo.INSTANCE, 1);
        }
        if (io.support(IO.OUT)) {
            setMaxSize(IO.OUT, EURecipeInfo.INSTANCE, 1);
        }
        return setMaxTooltips(3);
    }

    public GTRecipeType setMaxSize(IO io, RecipeInfo cap, int max) {
        if (io == IO.IN || io == IO.BOTH) {
            maxInputs.put(cap, max);
        }
        if (io == IO.OUT || io == IO.BOTH) {
            maxOutputs.put(cap, max);
        }
        return this;
    }

    public GTRecipeType setSlotOverlay(boolean isOutput, boolean isFluid, IGuiTexture slotOverlay) {
        this.recipeUI.setSlotOverlay(isOutput, isFluid, slotOverlay);
        return this;
    }

    public GTRecipeType setSlotOverlay(boolean isOutput, boolean isFluid, boolean isLast, IGuiTexture slotOverlay) {
        this.recipeUI.setSlotOverlay(isOutput, isFluid, isLast, slotOverlay);
        return this;
    }

    public GTRecipeType setProgressBar(ResourceTexture progressBar, ProgressTexture.FillDirection moveType) {
        this.recipeUI.setProgressBar(progressBar, moveType);
        return this;
    }

    public GTRecipeType setSteamProgressBar(SteamTexture progressBar, ProgressTexture.FillDirection moveType) {
        this.recipeUI.setSteamProgressBarTexture(progressBar);
        this.recipeUI.setSteamMoveType(moveType);
        return this;
    }

    public GTRecipeType setUiBuilder(BiConsumer<GTRecipeDefinition, WidgetGroup> uiBuilder) {
        this.recipeUI.setUiBuilder(uiBuilder);
        return this;
    }

    public GTRecipeType setMaxTooltips(int maxTooltips) {
        this.recipeUI.setMaxTooltips(maxTooltips);
        return this;
    }

    public GTRecipeType setXEIVisible(boolean XEIVisible) {
        this.category.setXEIVisible(XEIVisible);
        return this;
    }

    public GTRecipeType addDataInfo(Function<DataComponentMap, String> dataInfo) {
        this.dataInfos.add(dataInfo);
        return this;
    }

    /**
     * @param recipeLogic A function which is passed the normal findRecipe() result. Returns null if no valid recipe for
     *                    the custom logic is found.
     */
    public GTRecipeType addCustomRecipeLogic(ICustomRecipeLogic recipeLogic) {
        this.customRecipeLogicRunners.add(recipeLogic);
        return this;
    }

    @Override
    public String toString() {
        return registryName.toString();
    }

    public boolean search(RecipeHandlerUnit unit, IntLongMap map, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> canHandle) {
        if (noSearch) return false;
        if (db == null) initDB();
        return db.search(unit, map, canHandle);
    }

    protected void initDB() {
        var recipes = new ArrayList<>(this.recipes.values());
        proxyRecipes.forEach(t -> {
            if (t instanceof GTRecipeType type) {
                recipes.addAll(type.recipes.values());
            } else {
                var map = (Map<ResourceLocation, Recipe>) GTCEu.getMinecraftServer().getRecipeManager().byType((RecipeType) t);
                recipes.addAll(map.entrySet().stream().map(e -> this.toGTrecipe(e.getKey(), e.getValue())).toList());
            }
        });
        db = RecipeDB.build(new RecipeDB(), recipes);
    }

    public void clear() {
        if (db != null) {
            db.searchContext = new RecipeSearcher<>();
        }
    }

    protected GTRecipeDefinition toGTrecipe(ResourceLocation id, Recipe<?> recipe) {
        var builder = recipeBuilder(id);
        for (var ingredient : recipe.getIngredients()) {
            builder.inputItems(ingredient);
        }
        builder.outputItems(recipe.getResultItem(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)));
        if (recipe instanceof AbstractCookingRecipe cookingRecipe) {
            builder.duration(cookingRecipe.getCookingTime() / 4);
            builder.EUt(GTValues.VA[GTValues.LV]);
        }
        return builder.build();
    }

    public int getMaxInputs(RecipeInfo cap) {
        return maxInputs.getOrDefault(cap, 0);
    }

    public int getMaxOutputs(RecipeInfo cap) {
        return maxOutputs.getOrDefault(cap, 0);
    }

    //////////////////////////////////////
    // ***** Recipe Builder ******//
    //////////////////////////////////////
    public GTRecipeType prepareBuilder(Consumer<GTRecipeBuilder> onPrepare) {
        onPrepare.accept(recipeBuilder);
        return this;
    }

    public GTRecipeBuilder recipeBuilder(ResourceLocation id) {
        return recipeBuilder.copy(id);
    }

    public GTRecipeBuilder recipeBuilder(ResourceLocation id, Object... append) {
        if (append.length > 0) {
            String toAppend = Arrays.stream(append).map(Object::toString).map(FormattingUtil::toLowerCaseUnderscore).reduce("", (a, b) -> a + "_" + b);
            id = id.withSuffix(toAppend);
        }
        return recipeBuilder(id);
    }

    public GTRecipeBuilder recipeBuilder(String id) {
        return recipeBuilder(GTCEu.id(id));
    }

    public GTRecipeBuilder recipeBuilder(String id, Object... append) {
        return recipeBuilder(GTCEu.id(id), append);
    }

    public GTRecipeBuilder copyFrom(GTRecipeBuilder builder) {
        return recipeBuilder.copyFrom(builder);
    }

    public GTRecipeType onRecipeBuild(Consumer<GTRecipeBuilder> onBuild) {
        recipeBuilder.onSave(onBuild);
        return this;
    }

    public void addDataStickEntry(@NotNull String researchId, @NotNull GTRecipeDefinition recipe) {
        Collection<GTRecipeDefinition> collection = researchEntries.computeIfAbsent(researchId, k -> new OpenCacheHashSet<>());
        collection.add(recipe);
    }

    @Nullable
    public Collection<GTRecipeDefinition> getDataStickEntry(@NotNull String researchId) {
        return researchEntries.get(researchId);
    }

    public void buildRepresentativeRecipes() {
        for (ICustomRecipeLogic logic : customRecipeLogicRunners) {
            logic.buildRepresentativeRecipes();
        }
    }

    public void addToMainCategory(GTRecipeDefinition recipe) {
        addToCategoryMap(category, recipe);
    }

    public void addToCategoryMap(GTRecipeCategory category, GTRecipeDefinition recipe) {
        categoryMap.computeIfAbsent(category, k -> new ReferenceOpenHashSet<>()).add(recipe);
    }

    public Set<GTRecipeCategory> getCategories() {
        return Collections.unmodifiableSet(categoryMap.keySet());
    }

    public Set<GTRecipeDefinition> getRecipesInCategory(GTRecipeCategory category) {
        return categoryMap.getOrDefault(category, Collections.emptySet());
    }

    public void convertItem(ItemIngredient ingredient, IntLongMap map) {
        if (ingredient instanceof IntCircuitIngredient circuitIngredient) {
            map.add(circuitIngredient.configuration, 1);
        } else if (ingredient.inner.isVanilla() && ingredient.inner.values.length == 1) {
            if (ingredient.inner.values[0] instanceof Ingredient.ItemValue itemValue) {
                map.add(itemValue.item.getItem().hashCode(), ingredient.amount);
            } else if (ingredient.inner.values[0] instanceof Ingredient.TagValue tagValue) {
                map.add(tagValue.tag.hashCode(), ingredient.amount);
            }
        }
    }

    public void convertItem(ItemStack stack, long amount, IntLongMap map) {
        var item = stack.getItem();
        map.add(item.hashCode(), amount);
        item.builtInRegistryHolder().tags.forEach(t -> map.add(t.hashCode(), amount));
        var nbt = stack.getTag();
        if (nbt != null && item == IntCircuitIngredient.PROGRAMMED_CIRCUIT) {
            if (nbt.tags.get(IntCircuitIngredient.Configuration) instanceof IntTag intTag) {
                map.add(intTag.getAsInt(), amount);
            }
        }
    }

    public void convertFluid(FluidIngredient ingredient, IntLongMap map) {
        if (ingredient.value instanceof Fluid fluid) {
            map.add(fluid.hashCode(), ingredient.amount);
        } else if (ingredient.value instanceof TagKey<?> tagKey) {
            map.add(tagKey.hashCode(), ingredient.amount);
        }
    }

    public void convertFluid(FluidStack stack, long amount, IntLongMap map) {
        var fluid = stack.getFluid();
        map.add(fluid.hashCode(), amount);
        fluid.builtInRegistryHolder().tags.forEach(t -> map.add(t.hashCode(), amount));
    }

    public interface ICustomRecipeLogic {

        /**
         * @return A custom recipe to run given the current holder's inputs. Will be called only if a registered
         *         recipe is not found to run. Return null if no recipe should be run by your logic.
         */
        @Nullable
        GTRecipeDefinition createCustomRecipe(IRecipeHandlerHolder holder, RecipeHandlerUnit unit);

        /**
         * Build all representative recipes in this method, then add them to the appropriate recipe category.
         * These are added to XEI to demonstrate the custom logic.
         * Not required, can NOOP if unneeded.
         */
        default void buildRepresentativeRecipes() {}
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeType setRecipeBuilder(final GTRecipeBuilder recipeBuilder) {
        this.recipeBuilder = recipeBuilder;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeType setRecipeUI(final GTRecipeTypeUI recipeUI) {
        this.recipeUI = recipeUI;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeType setSmallRecipeMap(final GTRecipeType smallRecipeMap) {
        this.smallRecipeMap = smallRecipeMap;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeType setIconSupplier(@Nullable final Supplier<ItemStack> iconSupplier) {
        this.iconSupplier = iconSupplier;
        return this;
    }

    @Nullable
    public Supplier<ItemStack> getIconSupplier() {
        return this.iconSupplier;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeType setSound(@Nullable final SoundEntry sound) {
        this.sound = sound;
        return this;
    }

    @Nullable
    public SoundEntry getSound() {
        return this.sound;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeType setScanner(final boolean isScanner) {
        this.isScanner = isScanner;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeType setHasResearchSlot(final boolean hasResearchSlot) {
        this.hasResearchSlot = hasResearchSlot;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeType setOffsetVoltageText(final boolean offsetVoltageText) {
        this.offsetVoltageText = offsetVoltageText;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeType setVoltageTextOffset(final int voltageTextOffset) {
        this.voltageTextOffset = voltageTextOffset;
        return this;
    }
}
