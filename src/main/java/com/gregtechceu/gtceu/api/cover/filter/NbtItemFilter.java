package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.PhantomItemSlot;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.elements.TextField;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import lombok.Getter;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 物品 NBT 过滤器。
 *
 * <ul>
 * <li>严格模式：物品的 NBT 和这里写的 NBT <b>完全相等</b>（连类型和列表顺序都算）；</li>
 * <li>部分模式：物品的 NBT 只要<b>包含</b>写的 NBT 就算命中——复合标签逐键递归，列表要求写的每个元素都能在物品的列表里
 * 找到（顺序无关，同一个元素不重复用），其余按相等比较。</li>
 * </ul>
 *
 * <p>
 * 形状参考 {@link TagItemFilter} / {@link TagFilter}：写的 NBT 存在过滤器物品自己的 NBT 里（{@code nbt} + {@code strict}），
 * 配置界面是「一行 SNBT 输入框 + 一个取 NBT 的虚拟槽 + 严格 / 部分开关」；虚拟槽里放物品就是把它的 NBT 抄进输入框。
 */
public class NbtItemFilter implements ItemFilter {

    /** 写的 NBT；{@code null} / 空视作没配（不匹配任何东西）。 */
    @Getter
    private CompoundTag nbt;
    /** 严格模式（完全相等）；关掉就是部分模式（包含）。 */
    @Getter
    private boolean strict;
    protected Consumer<ItemFilter> itemWriter = filter -> {};
    protected Consumer<ItemFilter> onUpdated = filter -> itemWriter.accept(filter);

    protected NbtItemFilter() {}

    public static NbtItemFilter loadFilter(ItemStack itemStack) {
        return loadFilter(Objects.requireNonNullElseGet(itemStack.getTag(), CompoundTag::new),
                filter -> itemStack.setTag(filter.saveFilter()));
    }

    private static NbtItemFilter loadFilter(CompoundTag tag, Consumer<ItemFilter> itemWriter) {
        var handler = new NbtItemFilter();
        handler.itemWriter = itemWriter;
        handler.strict = tag.getBoolean("strict");
        var written = tag.getCompound("nbt");
        handler.nbt = written.isEmpty() ? null : written;
        return handler;
    }

    @Override
    public void setOnUpdated(Consumer<ItemFilter> onUpdated) {
        this.onUpdated = filter -> {
            this.itemWriter.accept(filter);
            onUpdated.accept(filter);
        };
    }

    @Override
    public boolean isBlank() {
        return nbt == null || nbt.isEmpty();
    }

    @Override
    public CompoundTag saveFilter() {
        if (isBlank()) {
            return null;
        }
        var tag = new CompoundTag();
        tag.put("nbt", nbt);
        tag.putBoolean("strict", strict);
        return tag;
    }

    /** 输入框写的 SNBT；解析不出来就当没配。 */
    public void setNbtText(String snbt) {
        CompoundTag parsed = null;
        if (snbt != null && !snbt.isBlank()) {
            try {
                parsed = TagParser.parseTag(snbt);
            } catch (Exception ignored) {
                parsed = null;
            }
        }
        setNbt(parsed);
    }

    public void setNbt(CompoundTag nbt) {
        this.nbt = (nbt == null || nbt.isEmpty()) ? null : nbt;
        onUpdated.accept(this);
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
        onUpdated.accept(this);
    }

    public String getNbtText() {
        return nbt == null ? "" : nbt.toString();
    }

    @Override
    public boolean test(ItemStack itemStack) {
        if (isBlank()) return false;
        var stackTag = itemStack.getTag();
        return strict ? Objects.equals(stackTag, nbt) : containsNbt(stackTag, nbt);
    }

    @Override
    public int testItemCount(ItemStack itemStack) {
        return test(itemStack) ? Integer.MAX_VALUE : 0;
    }

    @Override
    public boolean supportsAmounts() {
        return false;
    }

    /** 部分模式：{@code input} 里能不能逐层找到 {@code written} 的全部内容。 */
    private static boolean containsNbt(Tag input, Tag written) {
        if (written == null) return true;
        if (input == null) return false;
        if (written instanceof CompoundTag writtenCompound) {
            if (!(input instanceof CompoundTag inputCompound)) return false;
            for (var e : writtenCompound.tags.entrySet()) {
                if (!containsNbt(inputCompound.get(e.getKey()),e.getValue())) return false;
            }
            return true;
        }
        if (written instanceof ListTag writtenList) {
            if (!(input instanceof ListTag inputList)) return false;
            boolean[] used = new boolean[inputList.size()];
            for (Tag writtenElement : writtenList) {
                boolean found = false;
                for (int i = 0; i < inputList.size(); i++) {
                    if (used[i]) continue;
                    if (containsNbt(inputList.get(i), writtenElement)) {
                        used[i] = true;
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }
        return Objects.equals(input, written);
    }

    @Override
    public Widget createConfigUI() {
        var handler = new ItemStackTransfer(1);
        var field = TextField.of(0, this::getNbtText, this::setNbtText);
        field.layout(l -> l.flexGrow(1));
        var slot = PhantomItemSlot.of(handler, 0).xeiPhantom();
        slot.setMaxStackSize(1);
        slot.tooltips("cover.nbt_filter.slot.tooltip");
        slot.setChangeListener(() -> {
            if (slot.isRemote()) return;
            var stack = handler.getStackInSlot(0);
            var tag = stack.getTag();
            if (tag != null && !tag.isEmpty()) setNbt(tag);
        });
        var inputRow = UIElement.centeredRow(UISizes.SLOT_SIZE).addChildren(field, slot);
        var mode = Form.controlRow("cover.nbt_filter.strict.enabled", Switch.of(this::isStrict, this::setStrict));
        return UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP)).addChildren(inputRow, mode);
    }
}
