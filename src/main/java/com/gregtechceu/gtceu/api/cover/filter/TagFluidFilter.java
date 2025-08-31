package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.ScrollablePhantomFluidWidget;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.utils.TagExprFilter;

import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TagFluidFilter extends TagFilter<FluidStack, FluidFilter> implements FluidFilter {

    private final Object2BooleanMap<Fluid> cache = new Object2BooleanOpenHashMap<>();

    protected TagFluidFilter() {}

    public static TagFluidFilter loadFilter(ItemStack itemStack) {
        return loadFilter(Objects.requireNonNullElseGet(itemStack.getTag(), CompoundTag::new),
                filter -> itemStack.setTag(filter.saveFilter()));
    }

    private static TagFluidFilter loadFilter(CompoundTag tag, Consumer<FluidFilter> itemWriter) {
        var handler = new TagFluidFilter();
        handler.itemWriter = itemWriter;
        handler.oreDictFilterExpression = tag.getString("oreDict");
        handler.matchExpr = null;
        handler.cache.clear();
        handler.matchExpr = TagExprFilter.parseExpression(handler.oreDictFilterExpression);
        return handler;
    }

    public void setOreDict(String oreDict) {
        cache.clear();
        super.setOreDict(oreDict);
    }

    @Override
    public boolean test(FluidStack fluidStack) {
        if (oreDictFilterExpression.isEmpty()) return false;
        if (cache.containsKey(fluidStack.getFluid())) return cache.getOrDefault(fluidStack.getFluid(), false);
        if (TagExprFilter.tagsMatch(matchExpr, fluidStack)) {
            cache.put(fluidStack.getFluid(), true);
            return true;
        }
        cache.put(fluidStack.getFluid(), false);
        return false;
    }

    @Override
    StackHandlerWidget<FluidStack, FluidFilter> getItemHandler() {
        return new TankSlot(new CustomFluidTank(1));
    }

    @Override
    public int testFluidAmount(FluidStack fluidStack) {
        return test(fluidStack) ? Integer.MAX_VALUE : 0;
    }

    @Override
    public boolean supportsAmounts() {
        return false;
    }

    protected static class TankSlot extends ScrollablePhantomFluidWidget implements StackHandlerWidget<FluidStack, FluidFilter> {

        CustomFluidTank fluidTank;

        public TankSlot(CustomFluidTank fluidTank) {
            super(fluidTank, 0,
                    90, 30,
                    18, 18,
                    fluidTank::getFluid, fluidTank::setFluid);
            setBackground(GuiTextures.SLOT);
            setClientSideWidget();
            this.fluidTank = fluidTank;
        }

        @Override
        public FluidStack getStack() {
            return fluidTank.getFluidInTank(0);
        }

        @Override
        public void setOnContentsChanged(Runnable runnable) {
            fluidTank.setOnContentsChanged(
                    () -> {
                        if (!isRemote()) {
                            writeUpdateInfo(12, buf -> buf.writeBoolean(true));
                        } else {
                            runnable.run();
                        }
                    });
        }

        @Override
        public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
            if (id == 12) {
                fluidTank.onContentsChanged();
                return;
            }
            super.readUpdateInfo(id, buffer);
        }

        @Override
        public boolean isEmpty() {
            return getStack().isEmpty();
        }

        @Override
        public Stream<TagKey<?>> getTags() {
            return getStack().getFluid().defaultFluidState().getTags().map(t -> (TagKey<?>) t);
        }
    }
}
