package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.ItemMaterialData;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.ItemMaterialInfo;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.expand.CWUExpander;
import com.gregtechceu.gtceu.api.recipe.expand.ContentExpander;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.recipe.research.ScannerBuilder;
import com.gregtechceu.gtceu.api.recipe.research.StationBuilder;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.recipe.condition.*;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.google.common.collect.ImmutableList;
import com.gto.datasynclib.datasream.DataComponentKey;
import com.gto.datasynclib.datasream.DataComponentMap;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import javax.annotation.ParametersAreNonnullByDefault;

@SuppressWarnings({ "unchecked", "UnusedReturnValue" })
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class GTRecipeBuilder {

    public static final DataComponentMap EMPTY_DATA = new DataComponentMap();

    public static GTRecipeBuilder RAW;

    @Nullable
    protected List<Content<ItemIngredient>> itemInputs;
    @Nullable
    protected List<Content<ItemIngredient>> itemOutputs;
    @Nullable
    protected List<Content<FluidIngredient>> fluidInputs;
    @Nullable
    protected List<Content<FluidIngredient>> fluidOutputs;

    @Nullable
    protected Set<RecipeCondition> conditions;
    @Nullable
    protected Set<ContentExpander> contentExpanders;
    @Nullable
    protected Set<ContentExpander> tickContentExpanders;
    @Nullable
    protected DataComponentMap data;

    @Getter
    protected ResourceLocation id;
    @Getter
    protected GTRecipeType recipeType;
    @Getter
    protected GTRecipeCategory recipeCategory;
    @Getter
    protected int duration = 100;

    @Getter
    protected long eut;
    @Getter
    protected int tier;

    protected int chance = Content.MAX_CHANCE;
    protected int tierChanceBoost = 0;

    @Nullable
    protected Consumer<GTRecipeBuilder> onSave;

    protected boolean itemMaterialInfo = false;
    protected boolean fluidMaterialInfo = false;
    protected boolean removePreviousMatInfo = false;

    @Nullable
    protected List<MaterialStack> tempItemMaterialStacks;
    @Nullable
    protected List<MaterialStack> tempFluidStacks;

    public GTRecipeBuilder(ResourceLocation id, GTRecipeType recipeType) {
        this(id);
        this.recipeType = recipeType;
        this.recipeCategory = recipeType.getCategory();
    }

    public GTRecipeBuilder(ResourceLocation id) {
        this.id = id;
    }

    public static GTRecipeBuilder ofRaw() {
        return RAW.copy();
    }

    public GTRecipeBuilder copy() {
        return copy(id);
    }

    public GTRecipeBuilder copy(String id) {
        return copy(GTCEu.id(id));
    }

    public GTRecipeBuilder copy(ResourceLocation id) {
        GTRecipeBuilder copy = new GTRecipeBuilder(id);
        copy.recipeType = this.recipeType;
        copy.recipeCategory = this.recipeCategory;
        if (this.itemInputs != null) copy.itemInputs = new ArrayList<>(this.itemInputs);
        if (this.itemOutputs != null) copy.itemOutputs = new ArrayList<>(this.itemOutputs);
        if (this.fluidInputs != null) copy.fluidInputs = new ArrayList<>(this.fluidInputs);
        if (this.fluidOutputs != null) copy.fluidOutputs = new ArrayList<>(this.fluidOutputs);
        if (this.conditions != null) copy.conditions = new ReferenceOpenHashSet<>(this.conditions);
        if (this.contentExpanders != null) copy.contentExpanders = new ReferenceOpenHashSet<>(this.contentExpanders);
        if (this.tickContentExpanders != null) copy.tickContentExpanders = new ReferenceOpenHashSet<>(this.tickContentExpanders);
        if (this.data != null) copy.data = this.data.clone();
        copy.duration = this.duration;
        copy.tier = this.tier;
        copy.eut = this.eut;
        copy.chance = this.chance;
        copy.onSave = this.onSave;
        return copy;
    }

    public GTRecipeBuilder copyFrom(GTRecipeBuilder builder) {
        return builder.copy(builder.id).onSave(null).recipeType(recipeType).category(recipeCategory);
    }

    public final List<Content<ItemIngredient>> getItemInputs() {
        return itemInputs == null ? Collections.emptyList() : itemInputs;
    }

    public final List<Content<ItemIngredient>> getItemOutputs() {
        return itemOutputs == null ? Collections.emptyList() : itemOutputs;
    }

    public final List<Content<FluidIngredient>> getFluidInputs() {
        return fluidInputs == null ? Collections.emptyList() : fluidInputs;
    }

    public final List<Content<FluidIngredient>> getFluidOutputs() {
        return fluidOutputs == null ? Collections.emptyList() : fluidOutputs;
    }

    public final Set<RecipeCondition> getConditions() {
        return conditions == null ? Collections.emptySet() : conditions;
    }

    public final Set<ContentExpander> getContentExpanders() {
        return contentExpanders == null ? Collections.emptySet() : contentExpanders;
    }

    public final Set<ContentExpander> getTickContentExpanders() {
        return tickContentExpanders == null ? Collections.emptySet() : tickContentExpanders;
    }

    public final DataComponentMap getData() {
        return data == null ? EMPTY_DATA : data;
    }

    public GTRecipeBuilder addCondition(RecipeCondition condition) {
        if (conditions == null) conditions = new ReferenceOpenHashSet<>();
        conditions.add(condition);
        return this;
    }

    public GTRecipeBuilder addCondition(Collection<RecipeCondition> conditions) {
        if (this.conditions == null) this.conditions = new ReferenceOpenHashSet<>();
        this.conditions.addAll(conditions);
        return this;
    }

    public GTRecipeBuilder addContentExpand(ContentExpander expand) {
        if (contentExpanders == null) contentExpanders = new ReferenceOpenHashSet<>();
        contentExpanders.add(expand);
        return this;
    }

    public GTRecipeBuilder addTickContentExpand(ContentExpander expand) {
        if (tickContentExpanders == null) tickContentExpanders = new ReferenceOpenHashSet<>();
        tickContentExpanders.add(expand);
        return this;
    }

    public GTRecipeBuilder EUt(long eu) {
        if (eu == 0) GTCEu.LOGGER.error("EUt can't be explicitly set to 0, id: {}", id);
        eut = eu;
        tier = GTUtil.getTierByVoltage(Math.abs(eu));
        return this;
    }

    public GTRecipeBuilder CWUt(long cwu) {
        if (cwu == 0) {
            GTCEu.LOGGER.error("CWUt can't be explicitly set to 0, id: {}", id);
        }
        if (cwu > 0) {
            addData(GTRecipeDataKeys.CWUT, cwu);
            addTickContentExpand(CWUExpander.INSTANCE);
        } else if (cwu < 0) {
            throw new IllegalArgumentException("CWUt can't be negative");
        }
        return this;
    }

    public GTRecipeBuilder totalCWU(int cwu) {
        this.durationIsTotalCWU(true);
        this.hideDuration(true);
        this.duration(cwu);
        return this;
    }

    public GTRecipeBuilder inputItems(Object input) {
        switch (input) {
            case Item item -> {
                return inputItems(item);
            }
            case Supplier<?> supplier when supplier.get() instanceof ItemLike item -> {
                return inputItems(item.asItem());
            }
            case ItemStack stack -> {
                return inputItems(stack);
            }
            case ItemIngredient ingredient -> {
                return inputItems(ingredient);
            }
            case Ingredient ingredient -> {
                return inputItems(ingredient);
            }
            case MaterialEntry entry -> {
                return inputItems(entry);
            }
            case TagKey<?> tag -> {
                return inputItems((TagKey<Item>) tag);
            }
            case MachineDefinition machine -> {
                return inputItems(machine);
            }
            default -> {
                GTCEu.LOGGER.error("""
                        Input item is not one of:
                        Item, Supplier<Item>, ItemStack, Ingredient, MaterialEntry, TagKey<Item>, MachineDefinition
                        id: {}""", id);
                return this;
            }
        }
    }

    public GTRecipeBuilder inputItems(Object input, int count) {
        switch (input) {
            case Item item -> {
                return inputItems(item, count);
            }
            case Supplier<?> supplier when supplier.get() instanceof ItemLike item -> {
                return inputItems(item.asItem(), count);
            }
            case ItemStack stack -> {
                return inputItems(stack.copyWithCount(count));
            }
            case Ingredient ingredient -> {
                return inputItems(ingredient, count);
            }
            case MaterialEntry entry -> {
                return inputItems(entry, count);
            }
            case TagKey<?> tag -> {
                return inputItems((TagKey<Item>) tag, count);
            }
            case MachineDefinition machine -> {
                return inputItems(machine, count);
            }
            default -> {
                GTCEu.LOGGER.error("""
                        Input item is not one of:
                        Item, Supplier<Item>, ItemStack, Ingredient, MaterialEntry, TagKey<Item>, MachineDefinition
                        id: {}""", id);
                return this;
            }
        }
    }

    public GTRecipeBuilder inputItems(Content<ItemIngredient> input) {
        if (itemInputs == null) itemInputs = new ArrayList<>();
        itemInputs.add(input);
        return this;
    }

    public GTRecipeBuilder inputItems(ItemIngredient input) {
        return inputItems(new Content<>(input));
    }

    public GTRecipeBuilder inputItems(Ingredient inputs) {
        return inputItems(ItemIngredient.of(inputs));
    }

    public GTRecipeBuilder inputItems(Ingredient inputs, int count) {
        return inputItems(ItemIngredient.of(inputs, count));
    }

    public GTRecipeBuilder inputItems(ItemStack input) {
        var matInfo = ItemMaterialData.getMaterialInfo(input.getItem());
        if (chance == Content.MAX_CHANCE) {
            if (matInfo != null) {
                if (tempItemMaterialStacks == null) tempItemMaterialStacks = new ArrayList<>();
                for (var matStack : matInfo.getMaterials()) {
                    tempItemMaterialStacks.add(matStack.multiply(input.getCount()));
                }
            }
        }
        return inputItems(ItemIngredient.of(input));
    }

    public GTRecipeBuilder inputItems(TagKey<Item> tag, int amount) {
        return inputItems(ItemIngredient.of(tag, amount));
    }

    public GTRecipeBuilder inputItems(TagKey<Item> tag) {
        return inputItems(tag, 1);
    }

    public GTRecipeBuilder inputItems(Item input, int amount) {
        return inputItems(new ItemStack(input, amount));
    }

    public GTRecipeBuilder inputItems(Item input) {
        return inputItems(input, 1);
    }

    public GTRecipeBuilder inputItems(Supplier<? extends Item> input) {
        return inputItems(input.get());
    }

    public GTRecipeBuilder inputItems(Supplier<? extends Item> input, int amount) {
        return inputItems(input.get(), amount);
    }

    public GTRecipeBuilder inputItems(TagPrefix orePrefix, Material material) {
        return inputItems(orePrefix, material, 1);
    }

    public GTRecipeBuilder inputItems(MaterialEntry input) {
        return inputItems(input, 1);
    }

    public GTRecipeBuilder inputItems(MaterialEntry input, int count) {
        return inputItems(input.tagPrefix(), input.material(), count);
    }

    public GTRecipeBuilder inputItems(TagPrefix tagPrefix, Material material, int count) {
        if (tagPrefix.isEmpty() || material.isNull()) {
            GTCEu.LOGGER.error("Tried to set input item stack that doesn't exist, id: {}, TagPrefix: {}, Material: {}, Count: {}", id, tagPrefix, material, count);
            return this;
        } else {
            if (tempItemMaterialStacks == null) tempItemMaterialStacks = new ArrayList<>();
            tempItemMaterialStacks.add(new MaterialStack(material, tagPrefix.getMaterialAmount(material) * count));
            tagPrefix.secondaryMaterials().forEach(mat -> tempItemMaterialStacks.add(mat.multiply(count)));
        }
        var item = ChemicalHelper.get(tagPrefix, material, count);
        if (item.isEmpty()) {
            TagKey<Item> tag = ChemicalHelper.getTag(tagPrefix, material);
            if (tag != null) {
                return inputItems(tag, count);
            }
        } else {
            return inputItems(ItemIngredient.of(item));
        }
        return this;
    }

    public GTRecipeBuilder inputItems(MachineDefinition machine) {
        return inputItems(machine, 1);
    }

    public GTRecipeBuilder inputItems(MachineDefinition machine, int count) {
        return inputItems(machine.asStack(count));
    }

    public GTRecipeBuilder outputItems(Object output) {
        switch (output) {
            case Item item -> {
                return outputItems(item);
            }
            case Supplier<?> supplier when supplier.get() instanceof ItemLike item -> {
                return outputItems(item.asItem());
            }
            case ItemStack stack -> {
                return outputItems(stack);
            }
            case MaterialEntry entry -> {
                return outputItems(entry);
            }
            case MachineDefinition machine -> {
                return outputItems(machine);
            }
            default -> {
                GTCEu.LOGGER.error("""
                        Output item is not one of:
                        Item, Supplier<Item>, ItemStack, MaterialEntry, MachineDefinition
                        id: {}""", id);
                return this;
            }
        }
    }

    public GTRecipeBuilder outputItems(Object output, int count) {
        switch (output) {
            case Item item -> {
                return outputItems(item, count);
            }
            case Supplier<?> supplier when supplier.get() instanceof ItemLike item -> {
                return outputItems(item.asItem(), count);
            }
            case ItemStack stack -> {
                return outputItems(stack.copyWithCount(count));
            }
            case MaterialEntry entry -> {
                return outputItems(entry, count);
            }
            case MachineDefinition machine -> {
                return outputItems(machine, count);
            }
            default -> {
                GTCEu.LOGGER.error("""
                        Output item is not one of:
                        Item, Supplier<Item>, ItemStack, MaterialEntry, MachineDefinition
                        id: {}""", id);
                return this;
            }
        }
    }

    public GTRecipeBuilder outputItems(ItemStack output) {
        return outputItems(ItemIngredient.of(output));
    }

    public GTRecipeBuilder outputItems(ItemStack... outputs) {
        for (ItemStack output : outputs) {
            outputItems(output);
        }
        return this;
    }

    public GTRecipeBuilder outputItems(Item output, int amount) {
        return outputItems(new ItemStack(output, amount));
    }

    public GTRecipeBuilder outputItems(Item output) {
        return outputItems(new ItemStack(output));
    }

    public GTRecipeBuilder outputItems(Supplier<? extends ItemLike> input) {
        return outputItems(new ItemStack(input.get().asItem()));
    }

    public GTRecipeBuilder outputItems(Supplier<? extends ItemLike> input, int amount) {
        return outputItems(new ItemStack(input.get().asItem(), amount));
    }

    public GTRecipeBuilder outputItems(TagPrefix orePrefix, Material material) {
        return outputItems(orePrefix, material, 1);
    }

    public GTRecipeBuilder outputItems(TagPrefix orePrefix, Material material, int count) {
        if (orePrefix.isEmpty() || material.isNull()) {
            GTCEu.LOGGER.error("Tried to set output item stack that doesn't exist, id: {}, TagPrefix: {}, Material: {}, Count: {}", id, orePrefix, material, count);
            return this;
        }
        var item = ChemicalHelper.get(orePrefix, material, count);
        if (item.isEmpty()) {
            GTCEu.LOGGER.error("Tried to set output item stack that doesn't exist, id: {}, TagPrefix: {}, Material: {}, Count: {}", id, orePrefix, material, count);
            return this;
        }
        return outputItems(item);
    }

    public GTRecipeBuilder outputItems(MaterialEntry entry) {
        return outputItems(entry.tagPrefix(), entry.material());
    }

    public GTRecipeBuilder outputItems(MaterialEntry entry, int count) {
        return outputItems(entry.tagPrefix(), entry.material(), count);
    }

    public GTRecipeBuilder outputItems(MachineDefinition machine) {
        return outputItems(machine, 1);
    }

    public GTRecipeBuilder outputItems(MachineDefinition machine, int count) {
        return outputItems(machine.asStack(count));
    }

    public GTRecipeBuilder outputItems(ItemIngredient ingredient) {
        return outputItems(new Content<>(ingredient));
    }

    public GTRecipeBuilder outputItems(Ingredient ingredient) {
        return outputItems(ItemIngredient.of(ingredient));
    }

    public GTRecipeBuilder outputItems(Content<ItemIngredient> content) {
        if (itemOutputs == null) itemOutputs = new ArrayList<>();
        itemOutputs.add(content);
        return this;
    }

    public GTRecipeBuilder notConsumable(ItemStack itemStack) {
        int lastChance = this.chance;
        this.chance = 0;
        inputItems(itemStack);
        this.chance = lastChance;
        return this;
    }

    public GTRecipeBuilder notConsumable(ItemIngredient ingredient) {
        int lastChance = this.chance;
        this.chance = 0;
        inputItems(ingredient);
        this.chance = lastChance;
        return this;
    }

    public GTRecipeBuilder notConsumable(Ingredient ingredient) {
        int lastChance = this.chance;
        this.chance = 0;
        inputItems(ingredient);
        this.chance = lastChance;
        return this;
    }

    public GTRecipeBuilder notConsumable(Item item) {
        int lastChance = this.chance;
        this.chance = 0;
        inputItems(item);
        this.chance = lastChance;
        return this;
    }

    public GTRecipeBuilder notConsumable(Supplier<? extends Item> item) {
        int lastChance = this.chance;
        this.chance = 0;
        inputItems(item);
        this.chance = lastChance;
        return this;
    }

    public GTRecipeBuilder notConsumable(TagPrefix orePrefix, Material material) {
        int lastChance = this.chance;
        this.chance = 0;
        inputItems(orePrefix, material);
        this.chance = lastChance;
        return this;
    }

    public GTRecipeBuilder notConsumable(TagPrefix orePrefix, Material material, int count) {
        int lastChance = this.chance;
        this.chance = 0;
        inputItems(orePrefix, material, count);
        this.chance = lastChance;
        return this;
    }

    public GTRecipeBuilder notConsumableFluid(FluidStack fluid) {
        return notConsumableFluid(FluidIngredient.of(fluid));
    }

    public GTRecipeBuilder notConsumableFluid(FluidIngredient ingredient) {
        int lastChance = this.chance;
        this.chance = 0;
        inputFluids(ingredient);
        this.chance = lastChance;
        return this;
    }

    public GTRecipeBuilder circuitMeta(int configuration) {
        if (configuration < 0 || configuration > IntCircuitBehaviour.CIRCUIT_MAX) {
            GTCEu.LOGGER.error("Circuit configuration must be in the bounds 0 - 32");
        }
        return notConsumable(IntCircuitIngredient.of(configuration));
    }

    public GTRecipeBuilder chancedInput(ItemStack stack, int chance, int tierChanceBoost) {
        if (checkChanceAndPrintError(chance)) {
            return this;
        }
        int lastChance = this.chance;
        int lastTierChanceBoost = this.tierChanceBoost;
        this.chance = chance;
        this.tierChanceBoost = tierChanceBoost;
        inputItems(stack);
        this.chance = lastChance;
        this.tierChanceBoost = lastTierChanceBoost;
        return this;
    }

    public GTRecipeBuilder chancedInput(FluidStack stack, int chance, int tierChanceBoost) {
        if (checkChanceAndPrintError(chance)) {
            return this;
        }
        int lastChance = this.chance;
        int lastTierChanceBoost = this.tierChanceBoost;
        this.chance = chance;
        this.tierChanceBoost = tierChanceBoost;
        inputFluids(stack);
        this.chance = lastChance;
        this.tierChanceBoost = lastTierChanceBoost;
        return this;
    }

    public GTRecipeBuilder chancedOutput(ItemStack stack, int chance, int tierChanceBoost) {
        if (checkChanceAndPrintError(chance)) {
            return this;
        }
        int lastChance = this.chance;
        int lastTierChanceBoost = this.tierChanceBoost;
        this.chance = chance;
        this.tierChanceBoost = tierChanceBoost;
        outputItems(stack);
        this.chance = lastChance;
        this.tierChanceBoost = lastTierChanceBoost;
        return this;
    }

    public GTRecipeBuilder chancedOutput(FluidStack stack, int chance, int tierChanceBoost) {
        if (checkChanceAndPrintError(chance)) {
            return this;
        }
        int lastChance = this.chance;
        int lastTierChanceBoost = this.tierChanceBoost;
        this.chance = chance;
        this.tierChanceBoost = tierChanceBoost;
        outputFluids(stack);
        this.chance = lastChance;
        this.tierChanceBoost = lastTierChanceBoost;
        return this;
    }

    public GTRecipeBuilder chancedOutput(TagPrefix tag, Material mat, int chance, int tierChanceBoost) {
        return chancedOutput(ChemicalHelper.get(tag, mat), chance, tierChanceBoost);
    }

    public GTRecipeBuilder chancedOutput(TagPrefix tag, Material mat, int count, int chance, int tierChanceBoost) {
        return chancedOutput(ChemicalHelper.get(tag, mat, count), chance, tierChanceBoost);
    }

    public GTRecipeBuilder inputFluids(Material material, int amount) {
        return inputFluids(material.getFluid(amount));
    }

    public GTRecipeBuilder inputFluids(FluidStack input) {
        var matStack = ChemicalHelper.getMaterial(input.getFluid());
        if (!matStack.isNull() && chance != 0 && chance == Content.MAX_CHANCE) {
            if (tempFluidStacks == null) tempFluidStacks = new ArrayList<>();
            tempFluidStacks.add(new MaterialStack(matStack, input.getAmount() * GTValues.M / GTValues.L));
        }
        return inputFluids(FluidIngredient.of(input));
    }

    public GTRecipeBuilder inputFluids(FluidIngredient inputs) {
        return inputFluids(new Content<>(inputs));
    }

    public GTRecipeBuilder inputFluids(Content<FluidIngredient> inputs) {
        if (fluidInputs == null) fluidInputs = new ArrayList<>();
        fluidInputs.add(inputs);
        return this;
    }

    public GTRecipeBuilder outputFluids(FluidStack output) {
        return outputFluids(FluidIngredient.of(output));
    }

    public GTRecipeBuilder outputFluids(FluidStack... outputs) {
        for (var output : outputs) {
            outputFluids(output);
        }
        return this;
    }

    public GTRecipeBuilder outputFluids(FluidIngredient outputs) {
        return outputFluids(new Content<>(outputs));
    }

    public GTRecipeBuilder outputFluids(Content<FluidIngredient> inputs) {
        if (fluidOutputs == null) fluidOutputs = new ArrayList<>();
        fluidOutputs.add(inputs);
        return this;
    }

    //////////////////////////////////////
    // ********** DATA ***********//

    /// ///////////////////////////////////
    public <T> GTRecipeBuilder addData(DataComponentKey<T> key, T data) {
        if (this.data == null) this.data = new DataComponentMap();
        this.data.put(key, data);
        return this;
    }

    public GTRecipeBuilder blastFurnaceTemp(int blastTemp) {
        return addData(GTRecipeDataKeys.EBF_TEMP, blastTemp);
    }

    public GTRecipeBuilder explosivesAmount(int explosivesAmount) {
        return inputItems(new ItemStack(Blocks.TNT, explosivesAmount));
    }

    public GTRecipeBuilder explosivesType(ItemStack explosivesType) {
        return inputItems(explosivesType);
    }

    public GTRecipeBuilder solderMultiplier(int multiplier) {
        return addData(GTRecipeDataKeys.SOLDER_MULTIPLIER, multiplier);
    }

    public GTRecipeBuilder disableDistilleryRecipes(boolean flag) {
        return addData(GTRecipeDataKeys.DISABLE_DISTILLERY, flag);
    }

    public GTRecipeBuilder fusionStartEU(long eu) {
        return addData(GTRecipeDataKeys.EU_TO_START, eu);
    }

    public GTRecipeBuilder researchScan(boolean isScan) {
        return addData(GTRecipeDataKeys.SCAN_FOR_RESEARCH, isScan);
    }

    public GTRecipeBuilder durationIsTotalCWU(boolean durationIsTotalCWU) {
        return addData(GTRecipeDataKeys.DURATION_IS_TOTAL_CWU, durationIsTotalCWU);
    }

    public GTRecipeBuilder hideDuration(boolean hideDuration) {
        return addData(GTRecipeDataKeys.HIDE_DURATION, hideDuration);
    }

    //////////////////////////////////////
    // ******* CONDITIONS ********//

    /// ///////////////////////////////////
    public GTRecipeBuilder cleanroom(CleanroomType cleanroomType) {
        return addCondition(CleanroomCondition.get(cleanroomType));
    }

    public GTRecipeBuilder dimension(ResourceLocation dimension, boolean reverse) {
        return dimension(ResourceKey.create(Registries.DIMENSION, dimension), reverse);
    }

    public GTRecipeBuilder dimension(ResourceLocation dimension) {
        return dimension(dimension, false);
    }

    public GTRecipeBuilder dimension(ResourceKey<Level> dimension, boolean reverse) {
        return addCondition(new DimensionCondition(reverse, dimension));
    }

    public GTRecipeBuilder dimension(ResourceKey<Level> dimension) {
        return dimension(dimension, false);
    }

    public GTRecipeBuilder biome(ResourceLocation biome, boolean reverse) {
        return biome(ResourceKey.create(Registries.BIOME, biome), reverse);
    }

    public GTRecipeBuilder biome(ResourceLocation biome) {
        return biome(biome, false);
    }

    public GTRecipeBuilder biome(ResourceKey<Biome> biome, boolean reverse) {
        return addCondition(new BiomeCondition(reverse, biome));
    }

    public GTRecipeBuilder biome(ResourceKey<Biome> biome) {
        return biome(biome, false);
    }

    public GTRecipeBuilder rain(float level, boolean reverse) {
        return addCondition(new RainingCondition(reverse, level));
    }

    public GTRecipeBuilder rain(float level) {
        return rain(level, false);
    }

    public GTRecipeBuilder thunder(float level, boolean reverse) {
        return addCondition(new ThunderCondition(reverse, level));
    }

    public GTRecipeBuilder thunder(float level) {
        return thunder(level, false);
    }

    public GTRecipeBuilder posY(int min, int max, boolean reverse) {
        return addCondition(new PositionYCondition(reverse, min, max));
    }

    public GTRecipeBuilder posY(int min, int max) {
        return posY(min, max, false);
    }

    public GTRecipeBuilder daytime(boolean isNight) {
        return addCondition(isNight ? DaytimeCondition.NIGHT : DaytimeCondition.DAY);
    }

    public GTRecipeBuilder daytime() {
        return daytime(false);
    }

    public GTRecipeBuilder adjacentBlock(Block A, Block B) {
        return addCondition(new AdjacentBlockCondition(false, A, B));
    }

    public GTRecipeBuilder adjacentBlock(Block A, Block B, boolean reverse) {
        return addCondition(new AdjacentBlockCondition(reverse, A, B));
    }

    public GTRecipeBuilder adjacentFluid(Fluid A, Fluid B) {
        return addCondition(new AdjacentFluidCondition(false, A, B));
    }

    public GTRecipeBuilder adjacentFluid(Fluid A, Fluid B, boolean reverse) {
        return addCondition(new AdjacentFluidCondition(reverse, A, B));
    }

    public GTRecipeBuilder ftbQuest(String questId, boolean isReverse) {
        if (!GTCEu.Mods.isFTBQuestsLoaded()) {
            GTCEu.LOGGER.error("FTBQuests is not loaded!");
            return this;
        }
        if (questId.isEmpty()) {
            GTCEu.LOGGER.error("Quest ID cannot be empty for recipe {}", this.id);
            return this;
        }
        long qID = QuestObjectBase.parseCodeString(questId);
        if (qID == 0L) {
            GTCEu.LOGGER.error("Quest {} not found for recipe {}", questId, this.id);
            return this;
        }
        return addCondition(new FTBQuestCondition(isReverse, qID));
    }

    public GTRecipeBuilder ftbQuest(String questId) {
        return ftbQuest(questId, false);
    }

    public GTRecipeBuilder researchStation(UnaryOperator<StationBuilder> research) {
        return addCondition(research.apply(new StationBuilder()).build(recipeType));
    }

    public GTRecipeBuilder scanner(UnaryOperator<ScannerBuilder> research) {
        return addCondition(research.apply(new ScannerBuilder()).build(recipeType));
    }

    public GTRecipeBuilder scanner(ItemStack researchStack) {
        return scanner(b -> b.researchStack(researchStack));
    }

    public GTRecipeBuilder scanner(ItemLike researchStack) {
        return scanner(b -> b.researchStack(researchStack));
    }

    public GTRecipeBuilder category(GTRecipeCategory category) {
        this.recipeCategory = category;
        return this;
    }

    public GTRecipeBuilder addMaterialInfo(boolean item) {
        this.itemMaterialInfo = item;
        return this;
    }

    public GTRecipeBuilder addMaterialInfo(boolean item, boolean fluid) {
        this.itemMaterialInfo = item;
        this.fluidMaterialInfo = fluid;
        return this;
    }

    public GTRecipeBuilder removePreviousMaterialInfo() {
        removePreviousMatInfo = true;
        return this;
    }

    public GTRecipeBuilder setTempItemMaterialStacks(List<MaterialStack> stacks) {
        tempItemMaterialStacks = stacks;
        return this;
    }

    public GTRecipeBuilder setTempFluidMaterialStacks(List<MaterialStack> stacks) {
        tempFluidStacks = stacks;
        return this;
    }

    public GTRecipeDefinition save() {
        if (onSave != null) {
            onSave.accept(this);
        }
        var recipe = build(true);
        recipeType.recipes.put(recipe.id, recipe);
        this.getConditions().stream().filter(ResearchCondition.class::isInstance).findAny().map(ResearchCondition.class::cast).ifPresent(condition -> this.recipeType.addDataStickEntry(condition.researchId, recipe));
        if (recipeType != null) {
            if (recipeCategory == null) {
                GTCEu.LOGGER.error("Recipes must have a category", new IllegalArgumentException());
            } else if (recipeCategory != GTRecipeCategory.DEFAULT && recipeCategory.getRecipeType() != recipeType) {
                GTCEu.LOGGER.error("Cannot apply Category with incompatible RecipeType", new IllegalArgumentException());
            }
            recipeCategory.addRecipe(recipe);
        }
        if (removePreviousMatInfo) {
            removeExistingMaterialInfo();
        }
        if (itemMaterialInfo || fluidMaterialInfo) {
            addOutputMaterialInfo();
        }
        tempItemMaterialStacks = null;
        tempFluidStacks = null;
        return recipe;
    }

    private void addOutputMaterialInfo() {
        var itemOutputs = getItemOutputs();
        var itemInputs = getItemInputs();
        if (itemOutputs.size() == 1 && (!itemInputs.isEmpty() || !(tempFluidStacks == null || tempFluidStacks.isEmpty()))) {
            var currOutput = itemOutputs.getFirst().inner;
            Item out = null;
            int outputCount = 0;
            if (!currOutput.isEmpty()) {
                ItemStack items = currOutput.getInnerItemStack();
                if (!items.isEmpty()) {
                    out = items.getItem();
                    outputCount = currOutput.getAmount();
                }
            }
            if (out == null || out == Items.AIR) {
                return;
            }
            Reference2LongOpenHashMap<Material> matStacks = new Reference2LongOpenHashMap<>();
            if (itemMaterialInfo && tempItemMaterialStacks != null) {
                for (var input : tempItemMaterialStacks) {
                    long am = input.amount() / outputCount;
                    matStacks.addTo(input.material(), am);
                }
            }
            if (fluidMaterialInfo && tempFluidStacks != null) {
                for (var input : tempFluidStacks) {
                    long am = input.amount() / outputCount;
                    matStacks.addTo(input.material(), am);
                }
            }
            if (!matStacks.isEmpty()) {
                ItemMaterialData.registerMaterialInfo(out, new ItemMaterialInfo(matStacks));
            }
        }
    }

    private void removeExistingMaterialInfo() {
        var itemOutputs = getItemOutputs();
        if (itemOutputs.size() == 1) {
            var currOutput = itemOutputs.getFirst().inner;
            Item out = null;
            if (!currOutput.isEmpty()) {
                ItemStack items = currOutput.getInnerItemStack();
                if (!items.isEmpty()) {
                    out = items.getItem();
                }
            }
            if (out == null || out == Items.AIR) {
                return;
            }
            var existingItemInfo = ItemMaterialData.getMaterialInfo(out);
            if (existingItemInfo != null) {
                ItemMaterialData.clearMaterialInfo(out);
            }
        }
    }

    public GTRecipe buildRawRecipe() {
        return new GTRecipe(recipeType.defaultDefinition, getItemInputs(), getItemOutputs(), getFluidInputs(), getFluidOutputs(), getData(), eut, tier, duration);
    }

    public GTRecipeDefinition build() {
        return build(false);
    }

    public GTRecipeDefinition build(boolean registered) {
        return new GTRecipeDefinition(registered, recipeType, recipeCategory, id.withPrefix(recipeType.registryName.getPath() + "/"), getItemInputs(), getItemOutputs(), getFluidInputs(), getFluidOutputs(), ImmutableList.copyOf(getConditions()), ImmutableList.copyOf(getContentExpanders()), ImmutableList.copyOf(getTickContentExpanders()), getData(), eut, tier, duration);
    }

    protected boolean checkChanceAndPrintError(int chance) {
        if (0 >= chance || chance > Content.MAX_CHANCE) {
            GTCEu.LOGGER.error("Chance cannot be less or equal to 0 or more than {}. Actual: {}.", Content.MAX_CHANCE, chance, new Throwable());
            return true;
        }
        return false;
    }

    //////////////////////////////////////
    // ******* Quick Query *******//

    /// ///////////////////////////////////
    public long EUt() {
        return eut;
    }

    public int getSolderMultiplier() {
        return Math.max(1, data.getInt(GTRecipeDataKeys.SOLDER_MULTIPLIER));
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeBuilder id(final ResourceLocation id) {
        this.id = id;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeBuilder recipeType(final GTRecipeType recipeType) {
        this.recipeType = recipeType;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeBuilder duration(final int duration) {
        this.duration = duration;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeBuilder tier(final int tier) {
        this.tier = tier;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeBuilder chance(final int chance) {
        this.chance = chance;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeBuilder tierChanceBoost(final int tierChanceBoost) {
        this.tierChanceBoost = tierChanceBoost;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public GTRecipeBuilder onSave(@Nullable final Consumer<GTRecipeBuilder> onSave) {
        this.onSave = onSave;
        return this;
    }
}
