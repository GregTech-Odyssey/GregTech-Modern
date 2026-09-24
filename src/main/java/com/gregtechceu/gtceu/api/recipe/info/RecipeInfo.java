package com.gregtechceu.gtceu.api.recipe.info;

import com.gregtechceu.gtceu.api.recipe.handler.IO;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.gto.datasynclib.datastream.DataComponentKey;

import java.util.Comparator;
import java.util.Locale;

/**
 * 配方内容种类的元信息：注册名、展示颜色、是否占用槽位、显示顺序，以及该种类在配方查看器里的渲染方式。
 *
 * <p>
 * 它同时是一个<b>身份键</b>（{@link DataComponentKey}）：注册名在同一个注册表里唯一，
 * 但比较时用的是<b>引用相等</b>而不是 {@code equals}。{@link RecipeInfoMap} 正是依赖这一点，
 * 让同名却来自不同注册表的键互不干扰。
 *
 * <p>
 * 每一种内容都以单例形式提供，例如 {@link ItemRecipeInfo#INSTANCE}、{@link FluidRecipeInfo#INSTANCE}、
 * {@link EURecipeInfo#INSTANCE}、{@link CWURecipeInfo#INSTANCE}。要新增内容种类，
 * 继承 {@link ContentRecipeInfo} 并注册到 {@code GTRegistries.RECIPE_INFOS}。
 */
public abstract class RecipeInfo extends DataComponentKey<Object> {

    /** 展示颜色（ARGB）。 */
    public final int color;
    /** 是否在配方界面的槽位区里为它生成槽位（见 {@code GTRecipeTypeUI#addInventorySlotGroup}）。 */
    public final boolean doRenderSlot;
    /** 显示顺序权重，见 {@link #COMPARATOR}。 */
    public final int sortIndex;

    /** 按 {@link #sortIndex} 升序排列，用于决定各类内容在配方界面里的先后。 */
    public static final Comparator<RecipeInfo> COMPARATOR = Comparator.comparingInt(o -> o.sortIndex);

    protected RecipeInfo(String name, int color, boolean doRenderSlot, int sortIndex) {
        super(name, null);
        this.color = color;
        this.doRenderSlot = doRenderSlot;
        this.sortIndex = sortIndex;
    }

    /** 生成槽位标识 {@code 种类_方向}（全小写），用作槽位控件的 id。 */
    public String slotName(IO io) {
        return "%s_%s".formatted(name, io.name().toLowerCase(Locale.ROOT));
    }

    /** {@link #slotName(IO)} 的带序号版本，用于同方向上的多个槽位。 */
    public String slotName(IO io, int index) {
        return "%s_%s_%s".formatted(name, io.name().toLowerCase(Locale.ROOT), index);
    }

    /** 本地化名称，取自 {@code recipe.capability.<name>.name}。 */
    public MutableComponent getName() {
        return Component.translatable("recipe.capability.%s.name".formatted(name));
    }

    /** 带 {@link #color} 着色的名称。 */
    public MutableComponent getColoredName() {
        return getName().withStyle(style -> style.withColor(this.color));
    }
}
