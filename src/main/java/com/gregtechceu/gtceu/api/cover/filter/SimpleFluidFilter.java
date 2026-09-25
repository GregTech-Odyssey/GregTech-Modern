package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;
import com.gregtechceu.gtceu.uipro.elements.PhantomFluidSlot;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;

import lombok.Getter;

import java.util.Arrays;
import java.util.function.Consumer;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SimpleFluidFilter implements FluidFilter {

    private static final int SCROLL_ID = SyncValueHost.ID_BASE - 14;

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
        var showAmount = grid.addSyncValue(SyncValue.of(() -> maxStackSize > 1, SyncValue.BOOLEAN, false));
        for (int row = 0; row < 3; row++) {
            var line = UIElement.row(UISizes.SLOT);
            for (int col = 0; col < 3; col++) {
                line.addChild(matchSlot(col * 3 + row, showAmount));
            }
            grid.addChild(line);
        }
        var options = UIElement.column(LayoutStyle.AUTO).layout(l -> l.flex(1).gapAll(UISizes.GAP)).addChildren(
                CoverUIs.controlRow("cover.filter.blacklist.enabled", Switch.of(this::isBlackList, this::setBlackList)),
                CoverUIs.controlRow("cover.item_filter.ignore_nbt.enabled", Switch.of(this::isIgnoreNbt, this::setIgnoreNbt)));
        return UIElement.row(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.SECTION_GAP)).addChildren(grid, options);
    }

    private PhantomFluidSlot matchSlot(int index, SyncValue<Boolean> showAmount) {
        var tank = new CustomFluidTank(1) {

            @Override
            public int getCapacity() {
                return SimpleFluidFilter.this.maxStackSize;
            }
        };
        tank.setFluid(matches[index]);
        var slot = new AmountSlot(tank, showAmount);
        slot.xeiPhantom();
        slot.setChangeListener(() -> {
            if (slot.isRemote()) return;
            matches[index] = tank.getFluidInTank(0);
            onUpdated.accept(this);
        });
        return slot;
    }

    private final class AmountSlot extends PhantomFluidSlot {

        private final CustomFluidTank tank;
        private final SyncValue<Boolean> showAmount;

        private AmountSlot(CustomFluidTank tank, SyncValue<Boolean> showAmount) {
            super(tank, 0, tank::getFluid, tank::setFluid);
            this.tank = tank;
            this.showAmount = showAmount;
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
            int change = delta;
            writeClientAction(SCROLL_ID, buf -> buf.writeInt(change));
            return true;
        }

        @Override
        public void handleClientAction(int id, FriendlyByteBuf buffer) {
            if (id != SCROLL_ID) {
                super.handleClientAction(id, buffer);
                return;
            }
            int delta = buffer.readInt();
            if (isDisabled() || SimpleFluidFilter.this.maxStackSize <= 1) return;
            FluidStack fluid = tank.getFluidInTank(0);
            if (fluid.isEmpty()) return;
            long amount = Math.min(Math.max((long) fluid.getAmount() + delta, 0), tank.getTankCapacity(0));
            if (amount <= 0) {
                tank.setFluidInTank(0, FluidStack.EMPTY);
            } else {
                fluid.setAmount((int) amount);
            }
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
