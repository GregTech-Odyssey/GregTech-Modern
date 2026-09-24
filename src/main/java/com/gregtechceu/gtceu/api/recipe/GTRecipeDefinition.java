package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.content.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.extension.RecipeExtension;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import com.gto.datasynclib.datastream.DataComponentKey;
import com.gto.datasynclib.datastream.DataComponentMap;
import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import com.gto.datasynclib.datastream.codec.DataCodec;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.datastream.data.ListData;
import com.gto.datasynclib.util.DataCodecs;
import com.gto.datasynclib.util.StreamCodecs;
import com.gto.recipesearch.IngredientTable;
import org.jetbrains.annotations.Range;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
/**
 * 配方定义：注册表里那份<b>只读模板</b>。
 *
 * <p>
 * 机器真正执行的是一份可变副本 {@link GTRecipe}（由 {@link #toRuntime()} 生成），
 * 因此超频、并行这类改动不会污染定义本身，同一条配方可以被多台机器同时使用。
 *
 * <p>
 * 它也是一个数据键（{@link DataComponentKey}），在数据流里用「配方类型 + 注册 id」标识；
 * 未注册的配方（例如自定义逻辑临时造出来的）只带类型，反序列化时会回落到
 * {@link GTRecipeType#defaultDefinition}。
 *
 * <p>
 * 一条配方由这几部分组成：四类内容（物品 / 流体的输入与输出）、{@link #conditions 触发条件}、
 * 若干 {@link #recipeExtensions 扩展}、若干 {@link #recipeModifiers 配方级修饰器}，
 * 以及电压、时长、等级等基本参数。
 */
public final class GTRecipeDefinition extends DataComponentKey<GTRecipeDefinition> {

    public static final ByteStreamCodec<GTRecipeDefinition> STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public GTRecipeDefinition decode(FriendlyByteBuf buf) {
            var type = GTRegistries.RECIPE_TYPES.streamCodec().decode(buf);
            if (buf.readBoolean()) {
                var id = StreamCodecs.RESOURCE_LOCATION_CODEC.decode(buf);
                var definition = type.recipes.get(id);
                return definition == null ? type.defaultDefinition : definition;
            }
            return type.defaultDefinition;
        }

        @Override
        public void encode(FriendlyByteBuf buf, GTRecipeDefinition recipe) {
            GTRegistries.RECIPE_TYPES.streamCodec().encode(buf, recipe.recipeType);
            if (recipe.registered) {
                buf.writeBoolean(true);
                StreamCodecs.RESOURCE_LOCATION_CODEC.encode(buf, recipe.id);
            } else {
                buf.writeBoolean(false);
            }
        }
    };

    public static final DataCodec<GTRecipeDefinition> DATA_CODEC = new DataCodec<>() {

        @Override
        public Data encode(GTRecipeDefinition recipe) {
            var list = new ListData(2);
            list.add(GTRegistries.RECIPE_TYPES.dataCodec().encode(recipe.recipeType));
            if (recipe.registered) {
                list.add(DataCodecs.RESOURCE_LOCATION_CODEC.encode(recipe.id));
            } else {
                list.addNull();
            }
            return list;
        }

        @Override
        public GTRecipeDefinition decode(Data data, int dataVersion) {
            var list = data.getList();
            var type = GTRegistries.RECIPE_TYPES.dataCodec().decode(list.getFirst(), dataVersion);
            var idData = list.get(1);
            if (idData.isNull()) return type.defaultDefinition;
            var id = DataCodecs.RESOURCE_LOCATION_CODEC.decode(idData, dataVersion);
            var definition = type.recipes.get(id);
            return definition == null ? type.defaultDefinition : definition;
        }
    };

    /** 检索用的原料索引表，由 {@code RecipeDB} 在构建数据库时写入。 */
    IngredientTable container;

    /** 是否注册在配方表里；为 {@code false} 时序列化只写配方类型，读回后回落到默认定义。 */
    public final boolean registered;
    /** 所属配方类型。 */
    public final GTRecipeType recipeType;
    /** 所属配方分类（用于配方查看器归类）。 */
    public final GTRecipeCategory recipeCategory;

    /** 注册 id；未注册的配方也用 id 作为数据键名。 */
    public final ResourceLocation id;

    /** 物品输入，数量为<b>单份</b>配方所需（未乘并行）。 */
    public final List<Content<ItemIngredient>> itemInputs;
    /** 物品输出，单份数量。 */
    public final List<Content<ItemIngredient>> itemOutputs;
    /** 流体输入，单份数量。 */
    public final List<Content<FluidIngredient>> fluidInputs;
    /** 流体输出，单份数量。 */
    public final List<Content<FluidIngredient>> fluidOutputs;
    /** 触发条件，见 {@code IRecipeHandlerHolder#checkConditions}。 */
    public final RecipeCondition[] conditions;
    /** 非 tick 扩展：在输入 / 输出匹配时结算。 */
    public final RecipeExtension[] recipeExtensions;
    /** tick 扩展：在每 tick 结算时使用。 */
    public final RecipeExtension[] tickRecipeExtensions;
    /** 配方自带的修饰器，在机器自身的修饰器之前按顺序应用。 */
    public final RecipeModifier[] recipeModifiers;
    /** 扩展数据（各 {@link RecipeExtension} 的值都存在这里）。 */
    public final DataComponentMap data;
    /** 概率加成函数：把内容里的概率按配方等级与运行等级提升，见 {@link ChanceBoostFunction}。 */
    public final ChanceBoostFunction chanceFunction;
    /** 电压：{@code > 0} 表示机器耗电，{@code < 0} 表示发电。 */
    public final long eut;
    /** 配方等级，决定机器等级门槛与概率加成基准。 */
    public final int tier;
    /** 单份配方的时长（tick）。 */
    public final int duration;
    /** 多条配方同时可用时的排序权重，越大越优先（见 {@code IRecipeHandlerHolder#prioritySearch}）。 */
    public final int priority;

    public GTRecipeDefinition(boolean registered,
                              GTRecipeType recipeType,
                              GTRecipeCategory recipeCategory,
                              ResourceLocation id,
                              List<Content<ItemIngredient>> itemInputs,
                              List<Content<ItemIngredient>> itemOutputs,
                              List<Content<FluidIngredient>> fluidInputs,
                              List<Content<FluidIngredient>> fluidOutputs,
                              List<RecipeModifier> recipeModifiers,
                              List<RecipeCondition> conditions,
                              List<RecipeExtension> recipeExtensions,
                              List<RecipeExtension> tickRecipeExtensions,
                              DataComponentMap data,
                              ChanceBoostFunction chanceFunction,
                              long eut, int tier, int duration, int priority) {
        super(id.toString(), null);
        this.registered = registered;
        this.recipeType = recipeType;
        this.recipeCategory = recipeCategory;
        this.id = id;
        this.itemInputs = itemInputs;
        this.itemOutputs = itemOutputs;
        this.fluidInputs = fluidInputs;
        this.fluidOutputs = fluidOutputs;
        this.conditions = conditions.toArray(new RecipeCondition[0]);
        this.recipeExtensions = recipeExtensions.toArray(new RecipeExtension[0]);
        this.recipeModifiers = recipeModifiers.toArray(new RecipeModifier[0]);
        this.tickRecipeExtensions = tickRecipeExtensions.toArray(new RecipeExtension[0]);
        this.data = data;
        this.chanceFunction = chanceFunction;
        this.eut = eut;
        this.tier = tier;
        this.duration = duration;
        this.priority = priority;
    }

    /** 取耗电电压：{@link #eut} 为正时返回它本身，否则返回 {@code 0}。 */
    @Range(from = 0, to = Long.MAX_VALUE)
    public long getInputEUt() {
        var eu = eut;
        if (eu > 0) return eu;
        return 0;
    }

    /** 取发电电压：{@link #eut} 为负时返回它的相反数，否则返回 {@code 0}。 */
    @Range(from = 0, to = Long.MAX_VALUE)
    public long getOutputEUt() {
        var eu = eut;
        if (eu < 0) return -eu;
        return 0;
    }

    /**
     * 生成一份可执行的运行时副本：内容列表直接复用，{@link #data} 做一份拷贝，
     * 并行数、超频等级等可变状态使用 {@link GTRecipe} 的默认值。
     */
    public GTRecipe toRuntime() {
        return new GTRecipe(this, itemInputs, itemOutputs, fluidInputs, fluidOutputs, data.clone(), eut, tier, duration);
    }

    @Override
    public String toString() {
        return name;
    }
}
