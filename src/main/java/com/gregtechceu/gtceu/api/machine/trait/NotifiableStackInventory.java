package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.handler.PlanScratch;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.api.transfer.key.StackInventory;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.recipesearch.IntLongMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * 有状态单件物品仓（研究对象、数据载体、转子等）：内部是活体 ItemStack，作为配方成员走可回退的慢路径。
 */
public class NotifiableStackInventory extends NotifiableContentHandler implements IRecipeHandler, ICapabilityTrait, IKeyHandler<AEItemKey> {

    @Getter
    public final IO capabilityIO;
    @Setter
    @Getter
    @SuppressWarnings("unchecked")
    protected Predicate<@Nullable Direction> capabilityValidator = GTUtil.FAVORABLE;
    @SaveToDisk
    public final StackInventory storage;
    private final long[] take;
    private final ItemStack[] undo;

    public NotifiableStackInventory(MetaMachine machine, StackInventory storage, IO handlerIO, IO capabilityIO) {
        super(machine, handlerIO);
        this.storage = storage;
        this.capabilityIO = capabilityIO;
        this.take = new long[storage.size];
        this.undo = new ItemStack[storage.size];
        storage.setOnContentsChanged(this::onContentsChanged);
    }

    public NotifiableStackInventory(MetaMachine machine, int slots, IO handlerIO, IO capabilityIO) {
        this(machine, new StackInventory(slots), handlerIO, capabilityIO);
    }

    @Override
    public boolean handlesItems() {
        return handlerIO != IO.NONE;
    }

    @Override
    public long available(AEKeyType type, KeyIngredient ingredient) {
        if (type != AEKeyType.items()) return 0;
        long total = 0;
        for (var stack : storage.stacks) {
            if (!stack.isEmpty() && ingredient.test(stack)) total += stack.getCount();
        }
        return total;
    }

    @Override
    public long reserveInput(PlanScratch plan, int member, AEKeyType type, int entry, KeyIngredient ingredient, long need, boolean consume) {
        if (type != AEKeyType.items()) return 0;
        long got = 0;
        for (int s = 0; s < storage.size && got < need; s++) {
            var stack = storage.stacks[s];
            if (stack.isEmpty() || !ingredient.test(stack)) continue;
            long free = stack.getCount() - reserved(plan, member, s, false);
            if (free <= 0) continue;
            long t = Math.min(free, need - got);
            plan.logCustom(member, s, entry, t, type, consume, false);
            got += t;
        }
        return got;
    }

    @Override
    public boolean commitInput(PlanScratch plan, int member, AEKeyType type) {
        if (type != AEKeyType.items()) return true;
        var stacks = storage.stacks;
        for (int s = 0; s < storage.size; s++) {
            take[s] = reserved(plan, member, s, true);
            if (take[s] > stacks[s].getCount()) return false;
        }
        for (int s = 0; s < storage.size; s++) {
            undo[s] = take[s] > 0 ? stacks[s].copy() : null;
        }
        for (int s = 0; s < storage.size; s++) {
            if (take[s] > 0) storage.extract(s, stacks[s], (int) take[s], false);
        }
        return true;
    }

    @Override
    public void rollbackInput(PlanScratch plan, int member, AEKeyType type) {
        if (type != AEKeyType.items()) return;
        for (int s = 0; s < storage.size; s++) {
            if (undo[s] != null) {
                storage.setStackInSlot(s, undo[s]);
                undo[s] = null;
            }
        }
    }

    private static long reserved(PlanScratch plan, int member, int slot, boolean consumeOnly) {
        long r = 0;
        for (int i = 0; i < plan.logSize(); i++) {
            if (plan.logMember(i) == member && plan.logToken(i) == slot && !plan.logIsFluid(i) && (!consumeOnly || plan.logConsumes(i))) r += plan.logAmount(i);
        }
        return r;
    }

    @Override
    public boolean forEachKey(AEKeyType type, KeyVisitor visitor) {
        if (type != AEKeyType.items()) return false;
        for (var stack : storage.stacks) {
            if (!stack.isEmpty() && visitor.visit(Keys.item(stack), stack.getCount())) return true;
        }
        return false;
    }

    @Override
    public void fillSearchMap(@NotNull GTRecipeType type, @NotNull IntLongMap map) {
        for (var stack : storage.stacks) {
            if (!stack.isEmpty()) type.convertKey(Keys.item(stack), stack.getCount(), map);
        }
    }

    @Override
    public boolean updateEmpty() {
        return storage.isEmpty();
    }

    @Override
    public AEKeyType keyType() {
        return AEKeyType.items();
    }

    @Override
    public IKeyHandler<AEItemKey> unrestricted() {
        return storage;
    }

    @Override
    public int size() {
        return storage.size;
    }

    @Override
    public boolean fixedSize() {
        return storage.fixedSize();
    }

    @Override
    public @Nullable AEItemKey keyAt(int slot) {
        return storage.keyAt(slot);
    }

    @Override
    public long amountAt(int slot) {
        return storage.amountAt(slot);
    }

    @Override
    public long slotLimit(int slot) {
        return storage.slotLimit(slot);
    }

    @Override
    public long insert(int slot, AEItemKey key, long amount, boolean simulate) {
        return canCapInput() ? storage.insert(slot, key, amount, simulate) : 0;
    }

    @Override
    public long extract(int slot, AEItemKey key, long amount, boolean simulate) {
        return canCapOutput() ? storage.extract(slot, key, amount, simulate) : 0;
    }

    public NotifiableStackInventory setAvailable(boolean available) {
        this.isAvailable = available;
        return this;
    }
}
