package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.codec.data.DataKey;
import com.gregtechceu.gtceu.api.codec.data.DataKeys;
import com.gregtechceu.gtceu.api.codec.data.DataMap;
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
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentInner;
import com.gregtechceu.gtceu.api.recipe.content.TickContentMap;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.recipe.research.ScannerBuilder;
import com.gregtechceu.gtceu.api.recipe.research.StationBuilder;
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

import com.fast.fastcollection.O2OOpenCacheHashMap;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import javax.annotation.ParametersAreNonnullByDefault;

@SuppressWarnings({ "unchecked", "UnusedReturnValue" })
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class GTRecipeBuilder {

    public static final Map<ResourceLocation, GTRecipeDefinition> RECIPE_MAP = new O2OOpenCacheHashMap<>();

    public static GTRecipeBuilder RAW;

    public final RecipeCapabilityMap<List<Content>> input;
    public final RecipeCapabilityMap<List<Content>> output;
    public final TickContentMap ticks;
    public final List<RecipeCondition> conditions;
    public final DataMap data;

    public ResourceLocation id;
    public GTRecipeType recipeType;
    public GTRecipeCategory recipeCategory;

    public int duration = 100;
    public int tier;

    public int priority;

    protected int chance = Content.MAX_CHANCE;
    protected int tierChanceBoost = 0;

    @Nullable
    protected Consumer<GTRecipeBuilder> onSave;

    protected boolean itemMaterialInfo = false;
    protected boolean fluidMaterialInfo = false;
    protected boolean removePreviousMatInfo = false;

    protected List<MaterialStack> tempItemMaterialStacks = new ArrayList<>();
    protected List<MaterialStack> tempFluidStacks = new ArrayList<>();

    public GTRecipeBuilder(ResourceLocation id, GTRecipeType recipeType) {
        this(id);
        this.recipeType = recipeType;
        this.recipeCategory = recipeType.getCategory();
    }

    public GTRecipeBuilder(ResourceLocation id) {
        this.id = id;
        input = new RecipeCapabilityMap<>();
        output = new RecipeCapabilityMap<>();
        ticks = new TickContentMap();
        conditions = new ArrayList<>();
        data = new DataMap();
    }

    public GTRecipeBuilder(ResourceLocation id, GTRecipeBuilder builder) {
        this.id = id;
        this.input = new RecipeCapabilityMap<>(builder.input.item == null ? null : new ArrayList<>(builder.input.item), builder.input.fluid == null ? null : new ArrayList<>(builder.input.fluid));
        this.output = new RecipeCapabilityMap<>(builder.output.item == null ? null : new ArrayList<>(builder.output.item), builder.output.fluid == null ? null : new ArrayList<>(builder.output.fluid));
        this.ticks = new TickContentMap(builder.ticks);
        this.conditions = new ArrayList<>(builder.conditions);
        this.data = builder.data.clone();
        this.recipeType = builder.recipeType;
        this.recipeCategory = builder.recipeCategory;
        this.duration = builder.duration;
        this.tier = builder.tier;
        this.priority = builder.priority;
        this.chance = builder.chance;
        this.tierChanceBoost = builder.tierChanceBoost;
        this.onSave = builder.onSave;
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
        return new GTRecipeBuilder(id, this);
    }

    public GTRecipeBuilder copyFrom(GTRecipeBuilder builder) {
        return builder.copy(builder.id).onSave(null).recipeType(recipeType).category(recipeCategory);
    }

    protected Content makeContent(Object o) {
        return new Content(o, chance, tierChanceBoost);
    }

    public <T extends ContentInner> GTRecipeBuilder input(ContentRecipeCapability<T> capability, T obj) {
        input.computeIfAbsent(capability, c -> new ArrayList<>()).add(makeContent(capability.ofInner(obj)));
        return this;
    }

    public <T extends ContentInner> GTRecipeBuilder output(ContentRecipeCapability<T> capability, T obj) {
        output.computeIfAbsent(capability, c -> new ArrayList<>()).add(makeContent(capability.ofInner(obj)));
        return this;
    }

    public GTRecipeBuilder addCondition(RecipeCondition condition) {
        conditions.add(condition);
        return this;
    }

    public GTRecipeBuilder EUt(long eu) {
        if (eu == 0) {
            GTCEu.LOGGER.error("EUt can't be explicitly set to 0, id: {}", id);
        }
        ticks.put(DataKeys.EUT, eu);
        tier = GTUtil.getTierByVoltage(Math.abs(eu));
        return this;
    }

    public GTRecipeBuilder CWUt(int cwu) {
        if (cwu == 0) {
            GTCEu.LOGGER.error("CWUt can't be explicitly set to 0, id: {}", id);
        }
        if (cwu > 0) {
            ticks.put(DataKeys.CWUT, cwu);
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

    public GTRecipeBuilder inputItems(ItemIngredient input) {
        if (missingIngredientError(true, ItemRecipeCapability.CAP, input::isEmpty)) {
            return this;
        }
        return input(ItemRecipeCapability.CAP, input);
    }

    public GTRecipeBuilder inputItems(Ingredient inputs) {
        if (missingIngredientError(true, ItemRecipeCapability.CAP, inputs::isEmpty)) {
            return this;
        }
        return input(ItemRecipeCapability.CAP, ItemIngredient.of(inputs));
    }

    public GTRecipeBuilder inputItems(Ingredient inputs, int count) {
        if (missingIngredientError(true, ItemRecipeCapability.CAP, inputs::isEmpty)) {
            return this;
        }
        return input(ItemRecipeCapability.CAP, ItemIngredient.of(inputs, count));
    }

    public GTRecipeBuilder inputItems(ItemStack input) {
        if (missingIngredientError(true, ItemRecipeCapability.CAP, input::isEmpty)) {
            return this;
        } else {
            var matInfo = ItemMaterialData.getMaterialInfo(input.getItem());
            if (chance == Content.MAX_CHANCE) {
                if (matInfo != null) {
                    for (var matStack : matInfo.getMaterials()) {
                        tempItemMaterialStacks.add(matStack.multiply(input.getCount()));
                    }
                }
            }
        }
        return input(ItemRecipeCapability.CAP, ItemIngredient.of(input));
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
            return input(ItemRecipeCapability.CAP, ItemIngredient.of(item));
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
        if (missingIngredientError(false, ItemRecipeCapability.CAP, output::isEmpty)) {
            return this;
        }
        return output(ItemRecipeCapability.CAP, ItemIngredient.of(output));
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
        return output(ItemRecipeCapability.CAP, ingredient);
    }

    public GTRecipeBuilder outputItems(Ingredient ingredient) {
        return output(ItemRecipeCapability.CAP, ItemIngredient.of(ingredient));
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
        if (missingIngredientError(true, FluidRecipeCapability.CAP, input::isEmpty)) {
            return this;
        }
        var matStack = ChemicalHelper.getMaterial(input.getFluid());
        if (!matStack.isNull() && chance != 0 && chance == Content.MAX_CHANCE) {
            tempFluidStacks.add(new MaterialStack(matStack, input.getAmount() * GTValues.M / GTValues.L));
        }
        return input(FluidRecipeCapability.CAP, FluidIngredient.of(input));
    }

    public GTRecipeBuilder inputFluids(FluidIngredient inputs) {
        return input(FluidRecipeCapability.CAP, inputs);
    }

    public GTRecipeBuilder outputFluids(FluidStack output) {
        return output(FluidRecipeCapability.CAP, FluidIngredient.of(output));
    }

    public GTRecipeBuilder outputFluids(FluidStack... outputs) {
        for (var output : outputs) {
            outputFluids(output);
        }
        return this;
    }

    public GTRecipeBuilder outputFluids(FluidIngredient outputs) {
        return output(FluidRecipeCapability.CAP, outputs);
    }

    //////////////////////////////////////
    // ********** DATA ***********//

    /// ///////////////////////////////////
    public <T> GTRecipeBuilder addData(DataKey<T> key, T data) {
        this.data.put(key, data);
        return this;
    }

    public GTRecipeBuilder blastFurnaceTemp(int blastTemp) {
        return addData(DataKeys.EBF_TEMP, blastTemp);
    }

    public GTRecipeBuilder explosivesAmount(int explosivesAmount) {
        return inputItems(new ItemStack(Blocks.TNT, explosivesAmount));
    }

    public GTRecipeBuilder explosivesType(ItemStack explosivesType) {
        return inputItems(explosivesType);
    }

    public GTRecipeBuilder solderMultiplier(int multiplier) {
        return addData(DataKeys.SOLDER_MULTIPLIER, multiplier);
    }

    public GTRecipeBuilder disableDistilleryRecipes(boolean flag) {
        return addData(DataKeys.DISABLE_DISTILLERY, flag);
    }

    public GTRecipeBuilder fusionStartEU(long eu) {
        return addData(DataKeys.EU_TO_START, eu);
    }

    public GTRecipeBuilder durationIsTotalCWU(boolean durationIsTotalCWU) {
        return addData(DataKeys.DURATION_IS_TOTAL_CWU, durationIsTotalCWU);
    }

    public GTRecipeBuilder hideDuration(boolean hideDuration) {
        return addData(DataKeys.HIDE_DURATION, hideDuration);
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
        ResearchCondition condition = this.conditions.stream().filter(ResearchCondition.class::isInstance).findAny().map(ResearchCondition.class::cast).orElse(null);
        if (condition != null) {
            this.recipeType.addDataStickEntry(condition.researchId, recipe);
        }
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
        RECIPE_MAP.put(recipe.id, recipe);
        return recipe;
    }

    private void addOutputMaterialInfo() {
        var itemOutputs = output.getOrDefault(ItemRecipeCapability.CAP, new ArrayList<>());
        var itemInputs = input.getOrDefault(ItemRecipeCapability.CAP, new ArrayList<>());
        if (itemOutputs.size() == 1 && (!itemInputs.isEmpty() || !tempFluidStacks.isEmpty())) {
            var currOutput = ItemRecipeCapability.CAP.of(itemOutputs.getFirst());
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
            if (itemMaterialInfo) {
                for (var input : tempItemMaterialStacks) {
                    long am = input.amount() / outputCount;
                    matStacks.addTo(input.material(), am);
                }
            }
            if (fluidMaterialInfo) {
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
        var itemOutputs = output.get(ItemRecipeCapability.CAP);
        if (itemOutputs.size() == 1) {
            var currOutput = ItemRecipeCapability.CAP.of(itemOutputs.getFirst());
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
        return new GTRecipe(GTRecipeDefinition.DUMMY, input, output, ticks, duration, tier);
    }

    public GTRecipeDefinition build() {
        return build(false);
    }

    public GTRecipeDefinition build(boolean registered) {
        return new GTRecipeDefinition(registered, recipeType, id.withPrefix(recipeType.registryName.getPath() + "/"), input, output, ticks, conditions, data, duration, tier, priority);
    }

    protected void warnTooManyIngredients(RecipeCapability<?> capability, boolean isInput, Map<RecipeCapability<?>, List<Content>> table, int addedEntries) {
        var recipeCapabilityMax = isInput ? recipeType.maxInputs : recipeType.maxOutputs;
        if (!recipeCapabilityMax.containsKey(capability)) return;
        int max = recipeCapabilityMax.getInt(capability);
        if (table.getOrDefault(capability, Collections.emptyList()).size() + addedEntries > max) {
            String io = isInput ? "inputs" : "outputs";
            GTCEu.LOGGER.warn("Recipe {} is trying to add more {} than its recipe type can support, Max {} {}: {}", id, io, capability.name, io, max);
        }
    }

    protected boolean missingIngredientError(boolean isInput, ContentRecipeCapability<?> cap, BooleanSupplier empty) {
        if (empty.getAsBoolean()) {
            String io = isInput ? "Input" : "Output";
            int size = output.getOrDefault(cap, Collections.emptyList()).size();
            GTCEu.LOGGER.error("{} {} {} of recipe {} is empty", io, cap.name, size, id);
            return true;
        }
        return false;
    }

    protected boolean checkChanceAndPrintError(int chance) {
        if (0 >= chance || chance > ChanceLogic.getMaxChancedValue()) {
            GTCEu.LOGGER.error("Chance cannot be less or equal to 0 or more than {}. Actual: {}.", ChanceLogic.getMaxChancedValue(), chance, new Throwable());
            return true;
        }
        return false;
    }

    //////////////////////////////////////
    // ******* Quick Query *******//

    /// ///////////////////////////////////
    public long EUt() {
        return ticks.get(DataKeys.EUT);
    }

    public int getSolderMultiplier() {
        return Math.max(1, data.getOrDefaultData(DataKeys.SOLDER_MULTIPLIER, 0));
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
