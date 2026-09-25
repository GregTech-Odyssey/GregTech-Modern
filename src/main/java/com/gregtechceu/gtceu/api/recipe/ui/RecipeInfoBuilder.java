package com.gregtechceu.gtceu.api.recipe.ui;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * 配方页信息区的收集器：配方条件、配方扩展、配方修饰器、配方类型的附加内容都往这里追加。
 * <p>
 * 信息区是一块只读的状态面板（uipro {@code StatusPanel}），每条信息一行；配方要求的额外物品/流体
 * （所需线圈、维度、邻接流体、研究数据等）以展示槽的形式排在状态面板下方。
 * <p>
 * 同一套追加代码会跑两遍：一遍只数行数和槽数（算配方页尺寸，EMI 翻页排版时对整个类别的配方都要算一次），
 * 一遍才真正建控件。所以数值和槽位都以 {@link Supplier} 传入，只数数时不会被调用，建页后数值只取一次；
 * 追加与否只能取决于配方本身，两遍必须追加同样多的行和槽。
 */
public interface RecipeInfoBuilder {

    /**
     * 一行"名称 …… 数值"。{@code labelKey} 是名称的翻译键，数值单独给（不要用带 %s 的整句键）。
     */
    RecipeInfoBuilder line(String labelKey, Supplier<Component> value);

    /** 一行整句（没有名称），用于不好拆成名称和数值的说明。 */
    RecipeInfoBuilder sentence(Supplier<Component> text);

    /** 一行整句（没有名称）。 */
    default RecipeInfoBuilder sentence(Component text) {
        return sentence(() -> text);
    }

    default RecipeInfoBuilder link(Supplier<Component> text, Runnable onClick) {
        return sentence(text);
    }

    /**
     * 配方要求的额外展示槽（18 见方，只展示、不可取放），排在状态面板下方。
     * 控件应是 uipro {@code ItemSlot} / {@code FluidSlot}（或其他实现了 {@code IRecipeIngredientSlot} 的槽），
     * 这样在配方查看器里能按 EMI 的方式查配方、查用途。
     */
    RecipeInfoBuilder slot(Supplier<Widget> slot);
}
