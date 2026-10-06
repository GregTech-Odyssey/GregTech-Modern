package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import lombok.Getter;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 耐久过滤器：按「剩余耐久百分比」是否落在 {@code [minPercent, maxPercent]} 里匹配，{@link #isReverse() 反向}打开就是
 * 区间外。
 *
 * <p>
 * 没有耐久的物品（{@code maxDamage <= 0}）不参与匹配，反向也一样。判断用整数交叉相乘，不做除法也不产生临时对象。
 */
public class DurabilityItemFilter implements ItemFilter {

    /** 剩余耐久百分比下限（含）。 */
    @Getter
    private int minPercent = 0;
    /** 剩余耐久百分比上限（含）。 */
    @Getter
    private int maxPercent = 100;
    /** 反向：匹配落在区间外的（没有耐久的物品仍然不匹配）。 */
    @Getter
    private boolean reverse;
    protected Consumer<ItemFilter> itemWriter = filter -> {};
    protected Consumer<ItemFilter> onUpdated = filter -> itemWriter.accept(filter);

    protected DurabilityItemFilter() {}

    public static DurabilityItemFilter loadFilter(ItemStack itemStack) {
        return loadFilter(Objects.requireNonNullElseGet(itemStack.getTag(), CompoundTag::new),
                filter -> itemStack.setTag(filter.saveFilter()));
    }

    private static DurabilityItemFilter loadFilter(CompoundTag tag, Consumer<ItemFilter> itemWriter) {
        var handler = new DurabilityItemFilter();
        handler.itemWriter = itemWriter;
        handler.minPercent = clamp(tag.getInt("min"));
        handler.maxPercent = clamp(tag.contains("max") ? tag.getInt("max") : 100);
        if (handler.maxPercent < handler.minPercent) handler.maxPercent = handler.minPercent;
        handler.reverse = tag.getBoolean("reverse");
        return handler;
    }

    @Override
    public void setOnUpdated(Consumer<ItemFilter> onUpdated) {
        this.onUpdated = filter -> {
            this.itemWriter.accept(filter);
            onUpdated.accept(filter);
        };
    }

    /** 0~100、不反向＝不限制（这时不往物品里写 NBT）；但这仍然是有效配置，{@link #test} 不会因为它是「空」就不匹配。 */
    @Override
    public boolean isBlank() {
        return minPercent == 0 && maxPercent == 100 && !reverse;
    }

    @Override
    public CompoundTag saveFilter() {
        if (isBlank()) {
            return null;
        }
        var tag = new CompoundTag();
        tag.putInt("min", minPercent);
        tag.putInt("max", maxPercent);
        tag.putBoolean("reverse", reverse);
        return tag;
    }

    public void setMinPercent(int min) {
        minPercent = clamp(min);
        if (maxPercent < minPercent) maxPercent = minPercent;
        onUpdated.accept(this);
    }

    public void setMaxPercent(int max) {
        maxPercent = clamp(max);
        if (minPercent > maxPercent) minPercent = maxPercent;
        onUpdated.accept(this);
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
        onUpdated.accept(this);
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    @Override
    public boolean test(ItemStack itemStack) {
        int maxDamage = itemStack.getMaxDamage();
        if (maxDamage <= 0) return false;
        int remaining = maxDamage - itemStack.getDamageValue();
        // remaining / maxDamage * 100 落在 [min, max]：交叉相乘，避免除法/浮点
        boolean inRange = remaining * 100L >= (long) minPercent * maxDamage &&
                remaining * 100L <= (long) maxPercent * maxDamage;
        return reverse != inRange;
    }

    @Override
    public int testItemCount(ItemStack itemStack) {
        return test(itemStack) ? Integer.MAX_VALUE : 0;
    }

    @Override
    public boolean supportsAmounts() {
        return false;
    }

    @Override
    public Widget createConfigUI() {
        return UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP)).addChildren(
                Form.numberRow("cover.durability_filter.min",
                        NumberField.ofInt(LayoutStyle.AUTO, this::getMinPercent, this::setMinPercent, 0, 100),
                        "cover.durability_filter.info"),
                Form.numberRow("cover.durability_filter.max",
                        NumberField.ofInt(LayoutStyle.AUTO, this::getMaxPercent, this::setMaxPercent, 0, 100)),
                Form.controlRow("cover.durability_filter.reverse.enabled", Switch.of(this::isReverse, this::setReverse)));
    }
}
