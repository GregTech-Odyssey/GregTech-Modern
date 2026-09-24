package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.content.*;
import com.gregtechceu.gtceu.api.recipe.extension.RecipeExtension;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.datasynclib.GTDataFixer;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datastream.DataComponentMap;
import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import com.gto.datasynclib.datastream.codec.DataCodec;
import com.gto.datasynclib.datastream.codec.DataDecoder;
import com.gto.datasynclib.datastream.codec.DataEncoder;
import com.gto.datasynclib.datastream.data.*;
import com.gto.datasynclib.util.DataCodecs;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
/**
 * 运行时配方：{@link GTRecipeDefinition} 的一份<b>可被改写</b>的副本。
 *
 * <p>
 * 匹配、修饰（超频 / 并行 / 批处理）与执行都发生在这个对象上，内容数量、时长、电压会被就地修改；
 * 定义本身始终保持不变，所以同一条配方能被多台机器同时使用。
 *
 * <p>
 * 字段大致分两类：
 * <ul>
 * <li>描述「这条配方是什么」：四类内容列表、{@link #eut}、{@link #duration}、{@link #tier}、
 * {@link #data}（各扩展的数据）；</li>
 * <li>描述「这一次执行怎么跑」：{@link #parallels}、{@link #batchParallels}、{@link #ocLevel}、
 * {@link #contentParallel}、{@link #outputColor}、{@link #perfect}。</li>
 * </ul>
 *
 * <p>
 * {@link #equals} 与 {@link #hashCode} 只认 {@link #definition} 的身份，
 * 于是同一配方的不同并行数副本彼此「相等」；需要区分具体实例时请用引用比较。
 */
public final class GTRecipe {

    public static final ByteStreamCodec<GTRecipe> STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public GTRecipe decode(FriendlyByteBuf buf) {
            var recipe = new GTRecipe(GTRecipeDefinition.STREAM_CODEC.decode(buf), buf.readList(SerializerItemIngredient.INSTANCE::fromNetworkContent), buf.readList(SerializerItemIngredient.INSTANCE::fromNetworkContent), buf.readList(SerializerFluidIngredient.INSTANCE::fromNetworkContent), buf.readList(SerializerFluidIngredient.INSTANCE::fromNetworkContent), GTRecipeDataKeys.REGISTRY.decode(buf), buf.readVarLong(), buf.readVarInt(), buf.readVarInt());
            recipe.parallels = buf.readVarLong();
            recipe.batchParallels = buf.readVarLong();
            recipe.ocLevel = buf.readVarInt();
            return recipe;
        }

        @Override
        public void encode(FriendlyByteBuf buf, GTRecipe recipe) {
            GTRecipeDefinition.STREAM_CODEC.encode(buf, recipe.definition);
            buf.writeCollection(recipe.itemInputs, SerializerItemIngredient.INSTANCE::toNetworkContent);
            buf.writeCollection(recipe.itemOutputs, SerializerItemIngredient.INSTANCE::toNetworkContent);
            buf.writeCollection(recipe.fluidInputs, SerializerFluidIngredient.INSTANCE::toNetworkContent);
            buf.writeCollection(recipe.fluidOutputs, SerializerFluidIngredient.INSTANCE::toNetworkContent);
            GTRecipeDataKeys.REGISTRY.encode(buf, recipe.data);
            buf.writeVarLong(recipe.eut);
            buf.writeVarInt(recipe.tier);
            buf.writeVarInt(recipe.duration);
            buf.writeVarLong(recipe.parallels);
            buf.writeVarLong(recipe.batchParallels);
            buf.writeVarInt(recipe.ocLevel);
        }
    };

    public static final DataCodec<GTRecipe> DATA_CODEC = new DataCodec<>() {

        @Override
        public Data encode(GTRecipe recipe) {
            var list = new ListData(13);
            list.add(GTRecipeDefinition.DATA_CODEC, recipe.definition);
            list.add(DataEncoder.collection(SerializerItemIngredient.INSTANCE::toDataContent), recipe.itemInputs);
            list.add(DataEncoder.collection(SerializerItemIngredient.INSTANCE::toDataContent), recipe.itemOutputs);
            list.add(DataEncoder.collection(SerializerFluidIngredient.INSTANCE::toDataContent), recipe.fluidInputs);
            list.add(DataEncoder.collection(SerializerFluidIngredient.INSTANCE::toDataContent), recipe.fluidOutputs);
            list.add(GTRecipeDataKeys.REGISTRY.encode(recipe.data));
            list.addLong(recipe.eut);
            list.addInt(recipe.tier);
            list.addInt(recipe.duration);
            list.addLong(recipe.parallels);
            list.addLong(recipe.batchParallels);
            list.addInt(recipe.ocLevel);
            list.addInt(recipe.outputColor);
            return list;
        }

        @Override
        public GTRecipe decode(Data data, int dataVersion) {
            if (dataVersion == -1 && data instanceof StringMapData mapData) {
                var compoundTag = DataCodecs.COMPOUND_TAG_CODEC.decode(mapData, dataVersion);
                var definition = GTRecipe.EMPTY.definition;
                var duration = compoundTag.getInt("duration");
                var tier = compoundTag.getInt("tier");
                var eu = compoundTag.getLong("eu");
                List<Content<ItemIngredient>> itemInput = compoundTag.get("inputs") instanceof CompoundTag i ? fromNbt(ItemRecipeInfo.INSTANCE, i) : Collections.emptyList();
                List<Content<ItemIngredient>> itemOutput = compoundTag.get("outputs") instanceof CompoundTag i ? fromNbt(ItemRecipeInfo.INSTANCE, i) : Collections.emptyList();
                List<Content<FluidIngredient>> fluidInput = compoundTag.get("inputs") instanceof CompoundTag i ? fromNbt(FluidRecipeInfo.INSTANCE, i) : Collections.emptyList();
                List<Content<FluidIngredient>> fluidOutput = compoundTag.get("outputs") instanceof CompoundTag i ? fromNbt(FluidRecipeInfo.INSTANCE, i) : Collections.emptyList();
                return new GTRecipe(definition, itemInput, itemOutput, fluidInput, fluidOutput, new DataComponentMap(), eu, tier, duration);
            }
            if (data instanceof ByteArrayData arrayData) {
                data = Data.readData(arrayData.getByteArray());
            }
            var list = data.getList();
            var definition = GTRecipeDefinition.DATA_CODEC.decode(list.getFirst(), dataVersion);
            var recipeData = GTRecipeDataKeys.REGISTRY.decode(list.get(5), dataVersion);
            if (definition == definition.recipeType.defaultDefinition && !recipeData.isEmpty()) {
                List<RecipeExtension<?>> extensions = null;
                List<RecipeExtension<?>> tickExtensions = null;
                for (var k : recipeData.keySet()) {
                    if (k instanceof RecipeExtension<?> extension) {
                        if (extension.isTick) {
                            if (tickExtensions == null) tickExtensions = new ArrayList<>();
                            tickExtensions.add(extension);
                        } else {
                            if (extensions == null) extensions = new ArrayList<>();
                            extensions.add(extension);
                        }
                    }
                }
                if (extensions != null || tickExtensions != null) {
                    var b = definition.recipeType.recipeBuilder(definition.id);
                    if (extensions != null) extensions.forEach(b::addExtension);
                    if (tickExtensions != null) tickExtensions.forEach(b::addTickExtension);
                    definition = b.build();
                }
            }
            var recipe = new GTRecipe(definition, DataDecoder.notNullCollection(ArrayList::new, SerializerItemIngredient.INSTANCE::fromDataContent).decode(list.get(1), dataVersion), DataDecoder.notNullCollection(ArrayList::new, SerializerItemIngredient.INSTANCE::fromDataContent).decode(list.get(2), dataVersion), DataDecoder.notNullCollection(ArrayList::new, SerializerFluidIngredient.INSTANCE::fromDataContent).decode(list.get(3), dataVersion), DataDecoder.notNullCollection(ArrayList::new, SerializerFluidIngredient.INSTANCE::fromDataContent).decode(list.get(4), dataVersion), recipeData, list.get(6).getLong(), list.get(7).getInt(), list.get(8).getInt());
            recipe.parallels = list.get(9).getLong();
            recipe.batchParallels = list.get(10).getLong();
            recipe.ocLevel = list.get(11).getInt();
            recipe.outputColor = list.get(12).getInt();
            return recipe;
        }

        private static <T, C extends ContentInner<T>> List<Content<C>> fromNbt(RecipeInfo capability, CompoundTag tag) {
            if (tag.tags.get(capability.name) instanceof ListTag listTag) {
                var list = new ArrayList<Content<C>>();
                for (var t : listTag) {
                    Content<C> content = fromNbtContent(capability, t);
                    if (content != null) {
                        list.add(content);
                    }
                }
                if (!list.isEmpty()) return list;
            }
            return Collections.emptyList();
        }

        @Nullable
        @SuppressWarnings({ "rawtypes", "unchecked" }) // erasure cannot express that the capability key fixes the
                                                       // ingredient type
        private static <T, C extends ContentInner<T>> Content<C> fromNbtContent(RecipeInfo capability, @Nullable Tag tag) {
            if (tag instanceof CompoundTag compoundTag && compoundTag.tags.get("content") instanceof CompoundTag content) {
                var ingredient = capability == ItemRecipeInfo.INSTANCE ? ItemIngredient.fromNbt(content) : FluidIngredient.fromNbt(content);
                if (ingredient instanceof ContentInner inner && !inner.isEmpty()) return new Content(ingredient, getChance(compoundTag), getTierChanceBoost(compoundTag));
            }
            return null;
        }

        private static int getChance(CompoundTag tag) {
            if (tag.tags.get("chance") instanceof IntTag chance) {
                return chance.getAsInt();
            }
            return Content.MAX_CHANCE;
        }

        private static int getTierChanceBoost(CompoundTag tag) {
            if (tag.tags.get("tierChanceBoost") instanceof IntTag tierChanceBoost) {
                return tierChanceBoost.getAsInt();
            }
            return 0;
        }
    };

    /**
     * 空配方：{@code GTRecipeTypes.DUMMY_RECIPES} 默认定义生成的实例。
     *
     * <p>
     * 用于「当前没有真实配方」的纯内容操作（手动塞料 / 抽产物、条件检查等）。
     * 判断时请用<b>引用比较</b>（{@code recipe == GTRecipe.EMPTY}）：
     * {@link #equals} 只看 {@link #definition}，无法区分它和别的空定义配方。
     */
    public static final GTRecipe EMPTY = GTRecipeTypes.DUMMY_RECIPES.defaultDefinition.toRuntime();

    /** 对应的只读定义，记录这条配方「原本」的样子。 */
    public final GTRecipeDefinition definition;

    /** 物品输入，会被修饰与消耗就地改写。 */
    public List<Content<ItemIngredient>> itemInputs;
    /** 物品输出。 */
    public List<Content<ItemIngredient>> itemOutputs;
    /** 流体输入。 */
    public List<Content<FluidIngredient>> fluidInputs;
    /** 流体输出。 */
    public List<Content<FluidIngredient>> fluidOutputs;
    /** 扩展数据；{@link GTRecipeDefinition#data} 的副本，会被扩展就地改写（如 {@link #setCWUt}）。 */
    public DataComponentMap data;
    /** 当前电压：{@code > 0} 耗电，{@code < 0} 发电；超频会改写它。 */
    public long eut;
    /** 配方等级；概率加成的基准是 {@code tier + ocLevel}。 */
    public int tier;
    /** 当前时长（tick）；超频与批处理会改写它。 */
    public int duration;

    /** 已放大的并行倍率，{@link #modifier} 会把它累乘。 */
    public long parallels = 1;
    /** 并行数计算结果的缓存，由 {@code ParallelLogic} 写入；{@code 0} 表示本次还没算过。 */
    public long contentParallel;
    /** 批处理倍率，由 {@code RecipeModifier#batchProcessing} 写入，用于界面显示。 */
    public long batchParallels = 1;
    /** 本次超频级数，参与概率加成（{@code tier + ocLevel}）。 */
    public int ocLevel = 0;
    /** 产物写入哪个输出分组；{@code -1} 表示不指定。由匹配到的输入分组的颜色决定。 */
    public int outputColor = -1;
    /** 是否按「无损超频」处理：为 {@code true} 时时长系数取 0.25 而不是默认的 0.5。 */
    public boolean perfect;

    public GTRecipe(GTRecipeDefinition definition, List<Content<ItemIngredient>> itemInputs, List<Content<ItemIngredient>> itemOutputs, List<Content<FluidIngredient>> fluidInputs, List<Content<FluidIngredient>> fluidOutputs, DataComponentMap data, long eut, int tier, int duration) {
        this.definition = definition;
        this.itemInputs = itemInputs;
        this.itemOutputs = itemOutputs;
        this.fluidInputs = fluidInputs;
        this.fluidOutputs = fluidOutputs;
        this.data = data;
        this.eut = eut;
        this.tier = tier;
        this.duration = duration;
    }

    /**
     * 复制一份配方。
     *
     * <p>
     * 是<b>浅拷贝</b>：四个内容列表与 {@link #data} 都沿用同一批对象，
     * 只有 {@code eut}、{@code duration}、{@code tier} 等标量是新实例的字段。
     *
     * <p>
     * 注意副本不会继承运行状态——{@link #parallels}、{@link #batchParallels}、{@link #ocLevel}、
     * {@link #outputColor}、{@link #perfect}、{@link #contentParallel} 都会回到初始值，
     * 而内容列表里已经是放大过的数量。
     */
    public GTRecipe copy() {
        return new GTRecipe(definition, itemInputs, itemOutputs, fluidInputs, fluidOutputs, data, eut, tier, duration);
    }

    /** 把时长乘以 {@code multiplier}，结果至少为 1 tick。 */
    public void durationMultiplier(double multiplier) {
        this.duration = Math.max(1, (int) (duration * multiplier));
    }

    /**
     * 把电压乘以 {@code multiplier} 并保持正负号（耗电仍为正、发电仍为负）。
     *
     * <p>
     * 耗电侧结果至少为 1，发电侧则允许被乘小。
     */
    public void euMultiplier(double multiplier) {
        var eu = this.eut;
        if (eu > 0) {
            this.eut = Math.max(1, (long) (eu * multiplier));
        } else if (eu < 0) {
            this.eut = (long) (eu * multiplier);
        }
    }

    /**
     * 按并行数放大这条配方：内容数量、{@link #parallels} 以及各扩展的数据一起放大。
     *
     * <p>
     * 具体做四件事：{@link #parallels} 累乘 {@code multiplier}；四个内容列表用
     * {@link RecipeHelper#modifierContents} 按倍率放大数量；调用所有
     * {@link GTRecipeDefinition#recipeExtensions} 的
     * {@link com.gregtechceu.gtceu.api.recipe.extension.RecipeExtension#setParallel}。
     * {@code tick} 为 {@code true} 时还会额外把 {@link #eut} 乘上倍率，
     * 并让 {@link GTRecipeDefinition#tickRecipeExtensions} 一起放大——因为耗电与算力需求是每 tick 的，
     * 只有真正按并行重复执行时才需要跟着涨。
     *
     * @param multiplier 放大倍率，为 1 时直接返回
     * @param tick       是否同时放大每 tick 的消耗（能量、算力等）
     */
    public void modifier(@Range(from = 1, to = ParallelLogic.MAX_PARALLEL) long multiplier, boolean tick) {
        if (multiplier == 1) return;
        parallels *= multiplier;
        itemInputs = RecipeHelper.modifierContents(itemInputs, multiplier);
        itemOutputs = RecipeHelper.modifierContents(itemOutputs, multiplier);
        fluidInputs = RecipeHelper.modifierContents(fluidInputs, multiplier);
        fluidOutputs = RecipeHelper.modifierContents(fluidOutputs, multiplier);
        for (var extension : definition.recipeExtensions) {
            extension.setParallel(this, multiplier);
        }
        if (tick) {
            eut *= multiplier;
            for (var extension : definition.tickRecipeExtensions) {
                extension.setParallel(this, multiplier);
            }
        }
    }

    /** 直接设置电压（{@code > 0} 耗电，{@code < 0} 发电）。 */
    public void setEUt(long eu) {
        eut = eu;
    }

    /** 设置每 tick 的算力需求，写入扩展数据 {@code CWUT}。 */
    public void setCWUt(long cwu) {
        data.put(GTRecipeDataKeys.CWUT, cwu);
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

    /** 取每 tick 的算力需求；未设置或非正时返回 {@code 0}。 */
    @Range(from = 0, to = Long.MAX_VALUE)
    public long getInputCWUt() {
        var cwu = data.getLong(GTRecipeDataKeys.CWUT);
        if (cwu > 0) return cwu;
        return 0;
    }

    /** 只比较 {@link #definition} 的身份，因此同一配方的不同并行数副本互相「相等」。 */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GTRecipe recipe)) return false;
        return this.definition == recipe.definition;
    }

    /** 与 {@link #equals} 一致，取定义的身份哈希。 */
    @Override
    public int hashCode() {
        return System.identityHashCode(definition);
    }

    /** 输出定义名，便于调试。 */
    @Override
    public String toString() {
        return String.valueOf(definition);
    }

    /**
     * 从 NBT 还原配方，兼容两种格式：
     * <ul>
     * <li>{@link ByteArrayTag}——当前格式，走 {@link #DATA_CODEC}；</li>
     * <li>{@link CompoundTag}——旧格式，字段是扁平的 {@code inputs}/{@code outputs}，
     * 此时没有可还原的定义，一律挂在 {@link #EMPTY} 的定义上。</li>
     * </ul>
     *
     * @return 还原出的配方；格式不认识时返回 {@code null}
     */
    @Nullable
    public static GTRecipe fromNbt(@Nullable Tag t) {
        if (t instanceof ByteArrayTag tag) {
            return DATA_CODEC.decode(Data.readData(tag.getAsByteArray()), GTDataFixer.VERSION);
        } else if (t instanceof CompoundTag compoundTag) {
            var definition = GTRecipe.EMPTY.definition;
            var data = new DataComponentMap();
            var duration = compoundTag.getInt("duration");
            var tier = compoundTag.getInt("tier");
            var eu = compoundTag.getLong("eu");
            List<Content<ItemIngredient>> itemInput = compoundTag.get("inputs") instanceof CompoundTag i ? fromNbt(ItemRecipeInfo.INSTANCE, i) : Collections.emptyList();
            List<Content<ItemIngredient>> itemOutput = compoundTag.get("outputs") instanceof CompoundTag i ? fromNbt(ItemRecipeInfo.INSTANCE, i) : Collections.emptyList();
            List<Content<FluidIngredient>> fluidInput = compoundTag.get("inputs") instanceof CompoundTag i ? fromNbt(FluidRecipeInfo.INSTANCE, i) : Collections.emptyList();
            List<Content<FluidIngredient>> fluidOutput = compoundTag.get("outputs") instanceof CompoundTag i ? fromNbt(FluidRecipeInfo.INSTANCE, i) : Collections.emptyList();
            return new GTRecipe(definition, itemInput, itemOutput, fluidInput, fluidOutput, data, eu, tier, duration);
        }
        return null;
    }

    private static <T, C extends ContentInner<T>> List<Content<C>> fromNbt(RecipeInfo capability, CompoundTag tag) {
        if (tag.tags.get(capability.name) instanceof ListTag listTag) {
            var list = new ArrayList<Content<C>>();
            for (var t : listTag) {
                Content<C> content = fromNbtContent(capability, t);
                if (content != null) {
                    list.add(content);
                }
            }
            if (!list.isEmpty()) return list;
        }
        return Collections.emptyList();
    }

    @Nullable
    @SuppressWarnings({ "rawtypes", "unchecked" }) // erasure cannot express that the capability key fixes the
                                                   // ingredient type
    private static <T, C extends ContentInner<T>> Content<C> fromNbtContent(RecipeInfo capability, @Nullable Tag tag) {
        if (tag instanceof CompoundTag compoundTag && compoundTag.tags.get("content") instanceof CompoundTag content) {
            var ingredient = capability == ItemRecipeInfo.INSTANCE ? ItemIngredient.fromNbt(content) : FluidIngredient.fromNbt(content);
            if (ingredient instanceof ContentInner inner && !inner.isEmpty()) return new Content(ingredient, getChance(compoundTag), getTierChanceBoost(compoundTag));
        }
        return null;
    }

    private static int getChance(CompoundTag tag) {
        if (tag.tags.get("chance") instanceof IntTag chance) {
            return chance.getAsInt();
        }
        return Content.MAX_CHANCE;
    }

    private static int getTierChanceBoost(CompoundTag tag) {
        if (tag.tags.get("tierChanceBoost") instanceof IntTag tierChanceBoost) {
            return tierChanceBoost.getAsInt();
        }
        return 0;
    }

    public static ByteArrayTag toNbt(GTRecipe recipe) {
        return new ByteArrayTag(DATA_CODEC.encode(recipe).writeToBytes());
    }
}
