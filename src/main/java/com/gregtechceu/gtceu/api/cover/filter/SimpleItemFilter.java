package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.elements.PhantomItemSlot;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import lombok.Getter;

import java.util.Arrays;
import java.util.function.Consumer;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SimpleItemFilter implements ItemFilter {

    @Getter
    protected boolean isBlackList;
    @Getter
    protected boolean ignoreNbt;
    @Getter
    protected ItemStack[] matches = new ItemStack[9];
    protected Consumer<ItemFilter> itemWriter = filter -> {};
    protected Consumer<ItemFilter> onUpdated = filter -> itemWriter.accept(filter);
    @Getter
    protected int maxStackSize;

    protected SimpleItemFilter() {
        Arrays.fill(matches, ItemStack.EMPTY);
        maxStackSize = 1;
    }

    public static SimpleItemFilter loadFilter(ItemStack itemStack) {
        return loadFilter(itemStack.getOrCreateTag(), filter -> itemStack.setTag(filter.saveFilter()));
    }

    private static SimpleItemFilter loadFilter(CompoundTag tag, Consumer<ItemFilter> itemWriter) {
        var handler = new SimpleItemFilter();
        handler.itemWriter = itemWriter;
        handler.isBlackList = tag.getBoolean("isBlackList");
        handler.ignoreNbt = tag.getBoolean("matchNbt");
        var list = tag.getList("matches", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            handler.matches[i] = ItemStack.of((CompoundTag) list.get(i));
        }
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
        return !isBlackList && !ignoreNbt && Arrays.stream(matches).allMatch(ItemStack::isEmpty);
    }

    public CompoundTag saveFilter() {
        if (isBlank()) {
            return null;
        }
        var tag = new CompoundTag();
        tag.putBoolean("isBlackList", isBlackList);
        tag.putBoolean("matchNbt", ignoreNbt);
        var list = new ListTag();
        for (var match : matches) {
            list.add(match.save(new CompoundTag()));
        }
        tag.put("matches", list);
        return tag;
    }

    public void setBlackList(boolean blackList) {
        isBlackList = blackList;
        onUpdated.accept(this);
    }

    public void setIgnoreNbt(boolean ingoreNbt) {
        this.ignoreNbt = ingoreNbt;
        onUpdated.accept(this);
    }

    @Override
    public Widget createConfigUI() {
        var grid = UIElement.column(LayoutStyle.AUTO);
        var maxStack = grid.addSyncValue(SyncValue.ofInt(() -> maxStackSize, 1));
        for (int row = 0; row < 3; row++) {
            var line = UIElement.row(UISizes.SLOT);
            for (int col = 0; col < 3; col++) {
                line.addChild(matchSlot(col * 3 + row, maxStack));
            }
            grid.addChild(line);
        }
        var options = UIElement.column(LayoutStyle.AUTO).layout(l -> l.flex(1).gapAll(UISizes.GAP)).addChildren(
                CoverUIs.controlRow("cover.filter.blacklist.enabled", Switch.of(this::isBlackList, this::setBlackList)),
                CoverUIs.controlRow("cover.item_filter.ignore_nbt.enabled", Switch.of(this::isIgnoreNbt, this::setIgnoreNbt)));
        return UIElement.row(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.SECTION_GAP)).addChildren(grid, options);
    }

    private PhantomItemSlot matchSlot(int index, SyncValue<Integer> maxStack) {
        var handler = new ItemStackTransfer(matches[index]);
        var slot = new PhantomItemSlot(handler, 0) {

            @Override
            public void detectAndSendChanges() {
                setMaxStackSize(SimpleItemFilter.this.maxStackSize);
                super.detectAndSendChanges();
            }

            @Override
            @OnlyIn(Dist.CLIENT)
            public void updateScreen() {
                super.updateScreen();
                setMaxStackSize(maxStack.getValue());
            }
        };
        slot.xeiPhantom();
        slot.setChangeListener(() -> {
            if (slot.isRemote()) return;
            matches[index] = handler.getStackInSlot(0);
            onUpdated.accept(this);
        });
        return slot;
    }

    @Override
    public boolean test(ItemStack itemStack) {
        return testItemCount(itemStack) > 0;
    }

    @Override
    public int testItemCount(ItemStack itemStack) {
        int totalItemCount = getTotalConfiguredItemCount(itemStack);
        if (isBlackList) {
            return (totalItemCount > 0) ? 0 : Integer.MAX_VALUE;
        }
        return totalItemCount;
    }

    public int getTotalConfiguredItemCount(ItemStack itemStack) {
        int totalCount = 0;
        for (var candidate : matches) {
            if (ignoreNbt && ItemStack.isSameItem(candidate, itemStack)) {
                totalCount += candidate.getCount();
            } else if (ItemStack.isSameItemSameTags(candidate, itemStack)) {
                totalCount += candidate.getCount();
            }
        }
        return totalCount;
    }

    public void setMaxStackSize(int maxStackSize) {
        this.maxStackSize = maxStackSize;
        for (ItemStack match : matches) {
            match.setCount(Math.min(match.getCount(), maxStackSize));
        }
    }
}
