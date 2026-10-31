package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidAdapter;
import com.gregtechceu.gtceu.api.transfer.key.InfiniteKeySource;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.PhantomFluidSlot;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

@Getter
public class CreativeTankMachine extends QuantumTankMachine {

    private static final Component FLUID_LABEL = Component.translatable("gtceu.creative.tank.fluid");
    private static final Component MBPC_LABEL = Component.translatable("gtceu.creative.tank.mbpc");
    private static final Component TPC_LABEL = Component.translatable("gtceu.creative.tank.tpc");
    private static final Component ACTIVITY_ON = Component.translatable("gtceu.creative.activity.on");
    private static final Component ACTIVITY_OFF = Component.translatable("gtceu.creative.activity.off");

    private static final int CONFIG_AMOUNT = 1000;

    @SaveToDisk(defaultValue = "1000")
    @SyncToClient
    private int mBPerCycle = 1000;
    @SaveToDisk(defaultValue = "1")
    @SyncToClient
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

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (forDrop) {
            tag.putInt("mBPerCycle", mBPerCycle);
            tag.putInt("ticksPerCycle", ticksPerCycle);
        }
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains("mBPerCycle")) {
            mBPerCycle = tag.getInt("mBPerCycle");
        }
        if (tag.contains("ticksPerCycle")) {
            ticksPerCycle = tag.getInt("ticksPerCycle");
        }
    }

    public void setMBPerCycle(int value) {
        if (value < 1) return;
        mBPerCycle = value;
        onFluidChanged();
    }

    public void setTicksPerCycle(int value) {
        if (value < 1) return;
        ticksPerCycle = value;
        if (autoOutputSubs != null) autoOutputSubs.cycle = ticksPerCycle;
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
    public Widget createUIWidget() {
        var slot = PhantomFluidSlot.of(new ForgeFluidAdapter(cache.storage), 0, this::getStored, this::updateStored).xeiPhantom();
        slot.setShowAmount(false);
        var page = Form.page();
        page.addChild(Form.controlRow(FLUID_LABEL, slot));
        page.addChild(Form.numberRow(MBPC_LABEL,
                NumberField.ofInt(UISizes.CONTENT_WIDTH, this::getMBPerCycle, this::setMBPerCycle, 1, Integer.MAX_VALUE)));
        page.addChild(Form.numberRow(TPC_LABEL,
                NumberField.ofInt(UISizes.CONTENT_WIDTH, this::getTicksPerCycle, this::setTicksPerCycle, 1, Integer.MAX_VALUE)));
        page.addChild(Form.controlRow(() -> isWorkingEnabled() ? ACTIVITY_ON : ACTIVITY_OFF, Switch.of(this::isWorkingEnabled, this::setWorkingEnabled)));
        return page;
    }

    private class InfiniteCache extends FluidCache {

        private final InfiniteKeySource<AEFluidKey> source = new InfiniteKeySource<>(storage, false);

        public InfiniteCache(MetaMachine holder) {
            super(holder);
        }

        @Override
        public long slotLimit(int slot) {
            return storage.keyAt(0) != null ? mBPerCycle : 0;
        }

        @Override
        public long amountAt(int slot) {
            return storage.keyAt(0) != null ? mBPerCycle : 0;
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
