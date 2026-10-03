package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.PhantomFluidWidget;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidAdapter;
import com.gregtechceu.gtceu.api.transfer.key.InfiniteKeySource;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.syncdata.annotation.DropSaved;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.ItemHandlerHelper;

import appeng.api.stacks.AEFluidKey;
import com.gto.datasynclib.annotations.SaveToDisk;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

@Getter
public class CreativeTankMachine extends QuantumTankMachine {

    private static final int CONFIG_AMOUNT = 1000;

    @SaveToDisk(defaultValue = "1000")
    @DropSaved
    private int mBPerCycle = 1000;
    @SaveToDisk(defaultValue = "1")
    @DropSaved
    private int ticksPerCycle = 1;

    public CreativeTankMachine(MetaMachineBlockEntity holder) {
        super(holder, GTValues.MAX, 1);
    }

    protected FluidCache createCacheFluidHandler(Object... args) {
        return new InfiniteCache(this);
    }

    @Override
    public long getStoredAmount() {
        return (long) Math.ceil(1.0 * mBPerCycle / ticksPerCycle);
    }

    @Override
    protected void loadStored(@Nullable AEFluidKey key, long amount) {
        cache.storage.set(0, key, key == null ? 0 : CONFIG_AMOUNT);
    }

    private InteractionResult updateStored(FluidStack fluid) {
        var key = Keys.fluidType(fluid);
        cache.storage.set(0, key, key == null ? 0 : CONFIG_AMOUNT);
        return InteractionResult.SUCCESS;
    }

    private static int parseCycleValue(String value) {
        if (value.isEmpty()) return -1;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void setTicksPerCycle(String value) {
        int n = parseCycleValue(value);
        if (n < 1) return;
        ticksPerCycle = n;
        if (autoOutputSubs != null) autoOutputSubs.cycle = ticksPerCycle;
        onFluidChanged();
    }

    private void setmBPerCycle(String value) {
        int n = parseCycleValue(value);
        if (n < 1) return;
        mBPerCycle = n;
        onFluidChanged();
    }

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        var heldItem = player.getItemInHand(hand);
        if (hit.getDirection() == getFrontFacing() && !isRemote()) {
            var stored = cache.storage.keyAt(0);
            // Clear fluid if empty + shift-rclick
            if (heldItem.isEmpty()) {
                if (player.isCrouching() && stored != null) {
                    return updateStored(FluidStack.EMPTY);
                }
                return InteractionResult.PASS;
            }
            // If no fluid set and held-item has fluid, set fluid
            if (stored == null) {
                return FluidUtil.getFluidContained(heldItem).map(this::updateStored).orElse(InteractionResult.PASS);
            }
            // Need to make a fake source to fully fill held-item since our cache only allows mbPerTick extraction
            var source = new ForgeFluidAdapter(new InfiniteKeySource<>(cache.storage, false));
            ItemStack result = FluidUtil.tryFillContainer(heldItem, source, Integer.MAX_VALUE, player, true).getResult();
            if (!result.isEmpty() && heldItem.getCount() > 1) {
                ItemHandlerHelper.giveItemToPlayer(player, result);
                result = heldItem.copy();
                result.shrink(1);
            }
            if (!result.isEmpty()) {
                player.setItemInHand(hand, result);
                return InteractionResult.SUCCESS;
            } else {
                return FluidUtil.getFluidContained(heldItem).map(this::updateStored).orElse(InteractionResult.PASS);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public WidgetGroup createUIWidget() {
        var group = new WidgetGroup(0, 0, 176, 131);
        group.addWidget(new PhantomFluidWidget(new ForgeFluidAdapter(cache.storage), 0, 36, 6, 18, 18, this::getStored, this::updateStored).setShowAmount(false).setBackground(GuiTextures.FLUID_SLOT));
        group.addWidget(new LabelWidget(7, 9, "gtceu.creative.tank.fluid"));
        group.addWidget(new ImageWidget(7, 45, 154, 14, GuiTextures.DISPLAY));
        group.addWidget(new TextFieldWidget(9, 47, 152, 10, () -> String.valueOf(mBPerCycle), this::setmBPerCycle).setMaxStringLength(11).setNumbersOnly(1, Integer.MAX_VALUE));
        group.addWidget(new LabelWidget(7, 28, "gtceu.creative.tank.mbpc"));
        group.addWidget(new ImageWidget(7, 82, 154, 14, GuiTextures.DISPLAY));
        group.addWidget(new TextFieldWidget(9, 84, 152, 10, () -> String.valueOf(ticksPerCycle), this::setTicksPerCycle).setMaxStringLength(11).setNumbersOnly(1, Integer.MAX_VALUE));
        group.addWidget(new LabelWidget(7, 65, "gtceu.creative.tank.tpc"));
        group.addWidget(new SwitchWidget(7, 101, 162, 20, (clickData, value) -> setWorkingEnabled(value)).setTexture(new GuiTextureGroup(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("gtceu.creative.activity.off")), new GuiTextureGroup(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("gtceu.creative.activity.on"))).setPressed(isWorkingEnabled()));
        return group;
    }

    private class InfiniteCache extends FluidCache {

        private final InfiniteKeySource<AEFluidKey> source = new InfiniteKeySource<>(storage, false);

        public InfiniteCache(MetaMachine holder) {
            super(holder);
        }

        @Override
        public long slotLimit(int slot) {
            return source.slotLimit(slot);
        }

        @Override
        public long insert(int slot, AEFluidKey key, long amount, boolean simulate) {
            if (slot != 0 || amount <= 0) return 0;
            return storage.amountAt(0) > 0 && storage.rawKeyAt(0) == key ? amount : 0;
        }

        @Override
        public long extract(int slot, AEFluidKey key, long amount, boolean simulate) {
            return slot == 0 ? source.extract(0, key, amount, simulate) : 0;
        }

        @Override
        public long count(AEFluidKey key) {
            return source.count(key);
        }

        @Override
        protected long exportLimit() {
            return mBPerCycle;
        }
    }
}
