package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidAdapter;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.RPC;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.PhantomFluidSlot;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import lombok.Getter;

import java.util.Arrays;
import java.util.function.Consumer;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SimpleFluidFilter implements FluidFilter {

    private static final int MAX_SCROLL_DELTA = 100_000;

    @Getter
    protected boolean isBlackList;
    @Getter
    protected boolean ignoreNbt;
    @Getter
    protected FluidStack[] matches = new FluidStack[9];
    protected Consumer<FluidFilter> itemWriter = filter -> {};
    protected Consumer<FluidFilter> onUpdated = filter -> itemWriter.accept(filter);
    @Getter
    protected int maxStackSize = 1;

    protected SimpleFluidFilter() {
        Arrays.fill(matches, FluidStack.EMPTY);
    }

    public static SimpleFluidFilter loadFilter(ItemStack itemStack) {
        return loadFilter(itemStack.getOrCreateTag(), filter -> itemStack.setTag(filter.saveFilter()));
    }

    private static SimpleFluidFilter loadFilter(CompoundTag tag, Consumer<FluidFilter> itemWriter) {
        var handler = new SimpleFluidFilter();
        handler.itemWriter = itemWriter;
        handler.isBlackList = tag.getBoolean("isBlackList");
        handler.ignoreNbt = tag.getBoolean("matchNbt");
        var list = tag.getList("matches", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            handler.matches[i] = FluidStack.loadFluidStackFromNBT((CompoundTag) list.get(i));
        }
        return handler;
    }

    @Override
    public void setOnUpdated(Consumer<FluidFilter> onUpdated) {
        this.onUpdated = filter -> {
            this.itemWriter.accept(filter);
            onUpdated.accept(filter);
        };
    }

    @Override
    public boolean isBlank() {
        return !isBlackList && !ignoreNbt && Arrays.stream(matches).allMatch(FluidStack::isEmpty);
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
            list.add(match.writeToNBT(new CompoundTag()));
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
        var showAmount = grid.addSyncValue(SyncValue.ofBool(() -> maxStackSize > 1));
        for (int row = 0; row < 3; row++) {
            var line = UIElement.row(UISizes.SLOT_SIZE);
            for (int col = 0; col < 3; col++) {
                line.addChild(matchSlot(col * 3 + row, showAmount));
            }
            grid.addChild(line);
        }
        var options = UIElement.column(LayoutStyle.AUTO).layout(l -> l.flex(1).gapAll(UISizes.GAP)).addChildren(
                Form.controlRow("cover.filter.blacklist.enabled", Switch.of(this::isBlackList, this::setBlackList)),
                Form.controlRow("cover.item_filter.ignore_nbt.enabled", Switch.of(this::isIgnoreNbt, this::setIgnoreNbt)));
        return UIElement.row(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.SECTION_GAP)).addChildren(grid, options);
    }

    private PhantomFluidSlot matchSlot(int index, SyncValue<Boolean> showAmount) {
        var tank = KeyInventory.fluids(1, Math.max(maxStackSize, 1));
        var match = matches[index];
        tank.set(0, Keys.fluid(match), match.getAmount());
        var slot = new AmountSlot(tank, new ForgeFluidAdapter(tank), showAmount);
        slot.xeiPhantom();
        slot.setChangeListener(() -> {
            if (slot.isRemote()) return;
            var key = tank.keyAt(0);
            matches[index] = key == null ? FluidStack.EMPTY : Keys.toFluidStack(key, tank.amountAt(0));
            onUpdated.accept(this);
        });
        return slot;
    }

    private final class AmountSlot extends PhantomFluidSlot {

        private final KeyInventory<AEFluidKey> tank;
        private final SyncValue<Boolean> showAmount;
        private final RPC<Integer> scroll;

        private AmountSlot(KeyInventory<AEFluidKey> tank, ForgeFluidAdapter adapter, SyncValue<Boolean> showAmount) {
            super(adapter, 0, () -> adapter.getFluidInTank(0), fluid -> tank.set(0, Keys.fluid(fluid), fluid.getAmount()));
            this.tank = tank;
            this.showAmount = showAmount;
            this.scroll = addRPC(ByteStreamCodec.INT_CODEC, (player, delta) -> scrollAmount(delta))
                    .validate(delta -> delta >= -MAX_SCROLL_DELTA && delta <= MAX_SCROLL_DELTA);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void updateScreen() {
            super.updateScreen();
            setShowAmount(showAmount.getValue());
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
            if (!isMouseOverElement(mouseX, mouseY) || isDisabled() || !showAmount.getValue()) return false;
            int delta = wheelDelta > 0 ? 1 : -1;
            if (GTUtil.isShiftDown()) delta *= 10;
            if (GTUtil.isCtrlDown()) delta *= 100;
            if (!GTUtil.isAltDown()) delta *= 1000;
            scroll.send(delta);
            return true;
        }

        private void scrollAmount(int delta) {
            if (SimpleFluidFilter.this.maxStackSize <= 1) return;
            var key = tank.keyAt(0);
            if (key == null) return;
            long amount = Math.min(Math.max(tank.amountAt(0) + delta, 0), SimpleFluidFilter.this.maxStackSize);
            tank.set(0, key, amount);
        }
    }

    @Override
    public boolean test(FluidStack other) {
        return testFluidAmount(other) > 0L;
    }

    @Override
    public int testFluidAmount(FluidStack fluidStack) {
        int totalFluidAmount = getTotalConfiguredFluidAmount(fluidStack);
        if (isBlackList) {
            return (totalFluidAmount > 0) ? 0 : Integer.MAX_VALUE;
        }
        return totalFluidAmount;
    }

    public int getTotalConfiguredFluidAmount(FluidStack fluidStack) {
        int totalAmount = 0;
        for (var candidate : matches) {
            if (ignoreNbt && candidate.getFluid() == fluidStack.getFluid()) {
                totalAmount += candidate.getAmount();
            } else if (candidate.isFluidEqual(fluidStack)) {
                totalAmount += candidate.getAmount();
            }
        }
        return totalAmount;
    }

    public void setMaxStackSize(int maxStackSize) {
        this.maxStackSize = maxStackSize;
        for (FluidStack match : matches) {
            if (!match.isEmpty()) match.setAmount(Math.min(match.getAmount(), maxStackSize));
        }
    }
}
