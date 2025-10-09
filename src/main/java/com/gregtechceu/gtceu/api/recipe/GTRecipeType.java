package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.gui.SteamTexture;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.chance.boost.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.collection.O2OOpenCacheHashMap;
import com.gregtechceu.gtceu.utils.collection.OpenCacheHashSet;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

public class GTRecipeType implements RecipeType<GTRecipe> {

    public final ResourceLocation registryName;
    public final String group;
    public final Object2IntSortedMap<RecipeCapability<?>> maxInputs = new Object2IntAVLTreeMap<>(RecipeCapability.COMPARATOR);
    public final Object2IntSortedMap<RecipeCapability<?>> maxOutputs = new Object2IntAVLTreeMap<>(RecipeCapability.COMPARATOR);
    protected GTRecipeBuilder recipeBuilder;
    protected ChanceBoostFunction chanceFunction = ChanceBoostFunction.OVERCLOCK;
    protected GTRecipeTypeUI recipeUI = new GTRecipeTypeUI(this);
    protected GTRecipeType smallRecipeMap;
    @Nullable
    protected Supplier<ItemStack> iconSupplier;
    @Nullable
    protected SoundEntry sound;
    protected List<Function<CompoundTag, String>> dataInfos = new ObjectArrayList<>();
    protected boolean isScanner;
    // Does this recipe type have a research item slot? If this is true you MUST create a custom UI.
    protected boolean hasResearchSlot;
    protected final Map<RecipeType<?>, List<GTRecipe>> proxyRecipes;
    protected final GTRecipeCategory category;
    protected final Map<GTRecipeCategory, Set<GTRecipe>> categoryMap = new O2OOpenCacheHashMap<>();
    protected boolean offsetVoltageText = false;
    protected int voltageTextOffset = 20;
    protected final Map<String, Collection<GTRecipe>> researchEntries = new O2OOpenCacheHashMap<>();
    protected final List<ICustomRecipeLogic> customRecipeLogicRunners = new ObjectArrayList<>();
    public final Map<ResourceLocation, GTRecipe> recipes = new O2OOpenCacheHashMap<>();

    public GTRecipeType(ResourceLocation registryName, String group, RecipeType<?>... proxyRecipes) {
        this.registryName = registryName;
        this.group = group;
        this.category = GTRecipeCategory.registerDefault(this);
        recipeBuilder = new GTRecipeBuilder(registryName, this);
        // must be linked to stop json contents from shuffling
        Map<RecipeType<?>, List<GTRecipe>> map = new Reference2ReferenceOpenHashMap<>();
        for (RecipeType<?> proxyRecipe : proxyRecipes) {
            map.put(proxyRecipe, new ObjectArrayList<>());
        }
        this.proxyRecipes = map;
    }

    public GTRecipeType setMaxIOSize(int maxInputs, int maxOutputs, int maxFluidInputs, int maxFluidOutputs) {
        return setMaxSize(IO.IN, ItemRecipeCapability.CAP, maxInputs).setMaxSize(IO.IN, FluidRecipeCapability.CAP, maxFluidInputs).setMaxSize(IO.OUT, ItemRecipeCapability.CAP, maxOutputs).setMaxSize(IO.OUT, FluidRecipeCapability.CAP, maxFluidOutputs);
    }

    public GTRecipeType setEUIO(IO io) {
        if (io.support(IO.IN)) {
            setMaxSize(IO.IN, EURecipeCapability.CAP, 1);
        }
        if (io.support(IO.OUT)) {
            setMaxSize(IO.OUT, EURecipeCapability.CAP, 1);
        }
        return this;
    }

    public GTRecipeType setMaxSize(IO io, RecipeCapability<?> cap, int max) {
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

    public GTRecipeType setUiBuilder(BiConsumer<GTRecipe, WidgetGroup> uiBuilder) {
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

    public GTRecipeType addDataInfo(Function<CompoundTag, String> dataInfo) {
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

    @NotNull
    public Iterator<GTRecipe> searchRecipe(IRecipeCapabilityHolder holder, Predicate<GTRecipe> canHandle) {
        return recipes.values().parallelStream().filter(canHandle).iterator();
    }

    public int getMaxInputs(RecipeCapability<?> cap) {
        return maxInputs.getOrDefault(cap, 0);
    }

    public int getMaxOutputs(RecipeCapability<?> cap) {
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

    public void addDataStickEntry(@NotNull String researchId, @NotNull GTRecipe recipe) {
        Collection<GTRecipe> collection = researchEntries.computeIfAbsent(researchId, k -> new OpenCacheHashSet<>());
        collection.add(recipe);
    }

    @Nullable
    public Collection<GTRecipe> getDataStickEntry(@NotNull String researchId) {
        return researchEntries.get(researchId);
    }

    public void buildRepresentativeRecipes() {
        for (ICustomRecipeLogic logic : customRecipeLogicRunners) {
            logic.buildRepresentativeRecipes();
        }
    }

    public void addToMainCategory(GTRecipe recipe) {
        addToCategoryMap(category, recipe);
    }

    public void addToCategoryMap(GTRecipeCategory category, GTRecipe recipe) {
        categoryMap.computeIfAbsent(category, k -> new ObjectLinkedOpenHashSet<>()).add(recipe);
    }

    public Set<GTRecipeCategory> getCategories() {
        return Collections.unmodifiableSet(categoryMap.keySet());
    }

    public Set<GTRecipe> getRecipesInCategory(GTRecipeCategory category) {
        return Collections.unmodifiableSet(categoryMap.getOrDefault(category, Set.of()));
    }

    public interface ICustomRecipeLogic {

        /**
         * @return A custom recipe to run given the current holder's inputs. Will be called only if a registered
         *         recipe is not found to run. Return null if no recipe should be run by your logic.
         */
        @Nullable
        GTRecipe createCustomRecipe(IRecipeCapabilityHolder holder);

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

    public ChanceBoostFunction getChanceFunction() {
        return this.chanceFunction;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeType setChanceFunction(final ChanceBoostFunction chanceFunction) {
        this.chanceFunction = chanceFunction;
        return this;
    }

    public GTRecipeTypeUI getRecipeUI() {
        return this.recipeUI;
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

    public GTRecipeType getSmallRecipeMap() {
        return this.smallRecipeMap;
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

    public List<Function<CompoundTag, String>> getDataInfos() {
        return this.dataInfos;
    }

    public boolean isScanner() {
        return this.isScanner;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeType setScanner(final boolean isScanner) {
        this.isScanner = isScanner;
        return this;
    }

    public boolean isHasResearchSlot() {
        return this.hasResearchSlot;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeType setHasResearchSlot(final boolean hasResearchSlot) {
        this.hasResearchSlot = hasResearchSlot;
        return this;
    }

    public Map<RecipeType<?>, List<GTRecipe>> getProxyRecipes() {
        return this.proxyRecipes;
    }

    public GTRecipeCategory getCategory() {
        return this.category;
    }

    public Map<GTRecipeCategory, Set<GTRecipe>> getCategoryMap() {
        return this.categoryMap;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeType setOffsetVoltageText(final boolean offsetVoltageText) {
        this.offsetVoltageText = offsetVoltageText;
        return this;
    }

    public boolean isOffsetVoltageText() {
        return this.offsetVoltageText;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeType setVoltageTextOffset(final int voltageTextOffset) {
        this.voltageTextOffset = voltageTextOffset;
        return this;
    }

    public int getVoltageTextOffset() {
        return this.voltageTextOffset;
    }

    public List<ICustomRecipeLogic> getCustomRecipeLogicRunners() {
        return this.customRecipeLogicRunners;
    }
}
