package com.gregtechceu.gtceu.api.transfer.forge;

import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyHandlerList;
import com.gregtechceu.gtceu.api.transfer.key.KeyHandlerView;
import com.gregtechceu.gtceu.api.transfer.key.KeyIOView;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.me.storage.ExternalStorageFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * GT 物品存储对 Forge 的唯一出口：getStackInSlot 返回与存储共享的视图栈（存储写该槽即作废），读取不核对；
 * 视图被外部改写时在该槽下次经适配器写入/抽取/重建或 AE 按槽访问时核对（减少按抽取写回，其余恢复并告警）。
 */
public sealed class ForgeItemAdapter implements IItemHandler, ExternalStorageFacade.DirectKeyHandler permits FlatItemAdapter {

    static final int INSERT = 1;
    static final int EXTRACT = 2;
    static final int LIMIT = 4;

    final IKeyHandler<AEItemKey> handler;
    final boolean strictSlots;
    @Nullable
    ItemViews local;

    public ForgeItemAdapter(IKeyHandler<AEItemKey> handler) {
        this.handler = handler;
        this.strictSlots = strictSlots(handler);
    }

    static ForgeItemAdapter of(IKeyHandler<AEItemKey> handler) {
        var f = Flat.of(handler);
        return f == null ? new ForgeItemAdapter(handler) : new FlatItemAdapter(handler, f);
    }

    private static boolean strictSlots(IKeyHandler<?> h) {
        if (h instanceof KeyInventory<?>) return true;
        if (h instanceof KeyHandlerView<?> v) return strictSlots(v.getDelegate());
        if (h instanceof KeyHandlerList<?> list) {
            for (var m : list.handlers()) {
                if (!strictSlots(m)) return false;
            }
            return true;
        }
        return h.getClass() == NotifiableInventory.class;
    }

    public final IKeyHandler<AEItemKey> getHandler() {
        return handler;
    }

    @Override
    public int getSlots() {
        return handler.size();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        var lo = local;
        if (lo != null && slot >= 0 && slot < lo.lent.length) {
            lo.touch(slot);
            return handler.readSlot(slot, slot, lo);
        }
        return firstView(slot);
    }

    private ItemStack firstView(int slot) {
        if (slot < 0) return ItemStack.EMPTY;
        var key = handler.keyAt(slot);
        if (key == null) return ItemStack.EMPTY;
        return local(Math.max(slot + 1, handler.size())).view(slot, key, handler.amountAt(slot));
    }

    final ItemViews local(int n) {
        var lo = local;
        if (lo == null) local = lo = ItemViews.local(handler.unrestricted(), n);
        else if (n > lo.lent.length) local = lo = lo.grown(n);
        return lo;
    }

    private void touchLocal(int slot) {
        var lo = local;
        if (lo != null && slot >= 0 && slot < lo.lent.length) lo.touch(slot);
    }

    void touchKey(AEItemKey key) {
        var lo = local;
        if (lo != null) lo.touchKey(key);
    }

    final @Nullable AEItemKey keyFor(@Nullable AEItemKey current, ItemStack stack) {
        return current != null && current.getItem() == stack.getItem() && current.getTag() == stack.getTag() ? current : otherKey(current, stack);
    }

    private @Nullable AEItemKey otherKey(@Nullable AEItemKey current, ItemStack stack) {
        if (current == null) return AEItemKey.of(stack);
        if (current.getItem() != stack.getItem()) return strictSlots ? null : AEItemKey.of(stack);
        var t = stack.getTag();
        return t != null && t.equals(current.getTag()) ? current : AEItemKey.of(stack);
    }

    static @Nullable AEItemKey keyForEmpty(@Nullable AEItemKey residue, ItemStack stack) {
        return residue != null && residue.getItem() == stack.getItem() && residue.getTag() == stack.getTag() ? residue : residueOrNew(residue, stack);
    }

    private static @Nullable AEItemKey residueOrNew(@Nullable AEItemKey residue, ItemStack stack) {
        var t = stack.getTag();
        return residue != null && t != null && residue.getItem() == stack.getItem() && t.equals(residue.getTag()) ? residue : AEItemKey.of(stack);
    }

    static ItemStack rest(ItemStack stack, int count, long n) {
        if (n <= 0) return stack;
        if (n >= count) return ItemStack.EMPTY;
        return stack.copyWithCount(count - (int) n);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        int count = stack.getCount();
        if (count <= 0) return stack;
        touchLocal(slot);
        var key = keyFor(handler.keyAt(slot), stack);
        return key == null ? stack : rest(stack, count, handler.insert(slot, key, count, simulate));
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        touchLocal(slot);
        var key = handler.keyAt(slot);
        if (key == null) return ItemStack.EMPTY;
        long n = handler.extract(slot, key, amount, simulate);
        return n <= 0 ? ItemStack.EMPTY : key.toStack((int) n);
    }

    @Override
    public int getSlotLimit(int slot) {
        return Keys.saturatedInt(handler.slotLimit(slot));
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        var key = AEItemKey.of(stack);
        return key != null && (handler.spaceFor(slot, key) > 0 || handler.keyAt(slot) == key);
    }

    @Override
    public int getKeySlots() {
        return getSlots();
    }

    @Override
    public @Nullable AEKey getKeyInSlot(int slot) {
        touchLocal(slot);
        return handler.keyAt(slot);
    }

    @Override
    public long getAmountInSlot(int slot) {
        touchLocal(slot);
        return handler.amountAt(slot);
    }

    @Override
    public final long insertKey(AEKey what, long amount, Actionable mode) {
        return what instanceof AEItemKey key ? handler.insert(key, amount, mode == Actionable.SIMULATE) : 0;
    }

    @Override
    public final long extractKey(AEKey what, long amount, Actionable mode) {
        if (!(what instanceof AEItemKey key)) return 0;
        touchKey(key);
        return handler.extract(key, amount, mode == Actionable.SIMULATE);
    }

    static final class Flat {

        static final KeyInventory<?>[] NO_INVS = new KeyInventory<?>[0];
        private static final ClassValue<Integer> PLAIN_IO = new ClassValue<>() {

            @Override
            protected Integer computeValue(Class<?> type) {
                return (declaredByBase(type, "insert", int.class, AEKey.class, long.class, boolean.class) ? INSERT : 0) |
                        (declaredByBase(type, "extract", int.class, AEKey.class, long.class, boolean.class) ? EXTRACT : 0);
            }
        };

        private static final ClassValue<Boolean> PLAIN_LIMIT = new ClassValue<>() {

            @Override
            protected Boolean computeValue(Class<?> type) {
                return declaredByBase(type, "slotLimit", int.class);
            }
        };

        private static final ClassValue<Boolean> PLAIN_KEY_INSERT = new ClassValue<>() {

            @Override
            protected Boolean computeValue(Class<?> type) {
                return declaredByBase(type, "insert", AEKey.class, long.class, boolean.class);
            }
        };

        private static final ClassValue<Boolean> PLAIN_KEY_EXTRACT = new ClassValue<>() {

            @Override
            protected Boolean computeValue(Class<?> type) {
                return declaredByBase(type, "extract", AEKey.class, long.class, boolean.class);
            }
        };

        final int size;
        @Nullable
        final KeyInventory<?> inv0;
        final KeyInventory<?>[] invs;
        final int[] locals;
        final byte[] direct;
        final NotifiableInventory<?>[] owners;

        private Flat(int size, @Nullable KeyInventory<?> inv0, KeyInventory<?>[] invs, int[] locals, byte[] direct, NotifiableInventory<?>[] owners) {
            this.size = size;
            this.inv0 = inv0;
            this.invs = invs;
            this.locals = locals;
            this.direct = direct;
            this.owners = owners;
        }

        static @Nullable Flat of(IKeyHandler<?> handler) {
            if (!handler.fixedSize()) return null;
            var leaves = new ArrayList<Leaf>();
            if (!walk(handler, handler.keyType(), leaves, INSERT | EXTRACT)) return null;
            int size = 0;
            boolean any = false;
            for (var leaf : leaves) {
                size += leaf.size;
                if (leaf.inv != null) any = true;
            }
            if (!any || size != handler.size()) return null;
            var invs = new KeyInventory<?>[size];
            var locals = new int[size];
            var direct = new byte[size];
            var owners = new NotifiableInventory<?>[size];
            int s = 0;
            for (var leaf : leaves) {
                int d = leaf.direct | (leaf.owner == null || PLAIN_LIMIT.get(leaf.owner.getClass()) ? LIMIT : 0);
                for (int i = 0; i < leaf.size; i++, s++) {
                    if (leaf.inv == null) continue;
                    invs[s] = leaf.inv;
                    locals[s] = i;
                    direct[s] = (byte) d;
                    owners[s] = leaf.owner;
                }
            }
            var only = leaves.size() == 1 && size > 0 ? leaves.get(0).inv : null;
            return new Flat(size, only, invs, locals, direct, owners);
        }

        int uniformLimit() {
            long limit = -1;
            for (int s = 0; s < size; s++) {
                var inv = invs[s];
                if (inv == null || (direct[s] & LIMIT) == 0) return -1;
                long l = inv.slotLimit();
                if (limit < 0) limit = l;
                else if (l != limit) return -1;
            }
            return limit < 0 ? -1 : Keys.saturatedInt(limit);
        }

        static KeyInventory<?>[] distinct(KeyInventory<?>[] invs) {
            var out = new ArrayList<KeyInventory<?>>();
            for (var inv : invs) {
                if (inv == null) continue;
                boolean seen = false;
                for (var o : out) {
                    if (o == inv) {
                        seen = true;
                        break;
                    }
                }
                if (!seen) out.add(inv);
            }
            return out.toArray(NO_INVS);
        }

        static boolean plainKeyInsert(NotifiableInventory<?> owner) {
            return PLAIN_KEY_INSERT.get(owner.getClass());
        }

        static boolean plainKeyExtract(NotifiableInventory<?> owner) {
            return PLAIN_KEY_EXTRACT.get(owner.getClass());
        }

        private static boolean walk(IKeyHandler<?> h, AEKeyType type, List<Leaf> leaves, int access) {
            if (h instanceof KeyInventory<?> inv && inv.keyType() == type) {
                if (!inv.fixedSize()) return false;
                leaves.add(new Leaf(inv, null, inv.size(), access));
                return true;
            }
            if (h instanceof KeyHandlerView<?> view) return walk(view.getDelegate(), type, leaves, access & viewAccess(view));
            if (h instanceof KeyHandlerList<?> list) {
                for (var m : list.handlers()) {
                    if (!walk(m, type, leaves, access)) return false;
                }
                return true;
            }
            if (h instanceof NotifiableInventory<?> n) {
                var storage = n.readStorage();
                if (storage != null && storage.keyType() == type) {
                    if (!storage.fixedSize()) return false;
                    leaves.add(new Leaf(storage, n, storage.size(), access & PLAIN_IO.get(n.getClass())));
                    return true;
                }
            }
            if (!h.fixedSize()) return false;
            leaves.add(new Leaf(null, null, h.size(), 0));
            return true;
        }

        private static int viewAccess(KeyHandlerView<?> view) {
            var type = view.getClass();
            if (type != KeyHandlerView.class && type != KeyIOView.class) return 0;
            var io = view instanceof KeyIOView<?> v ? v : null;
            int access = 0;
            if (view.allowsInsert() && (io == null || io.getInFilter() == null)) access |= INSERT;
            if (view.allowsExtract() && (io == null || io.getOutFilter() == null)) access |= EXTRACT;
            return access;
        }

        private static boolean declaredByBase(Class<?> type, String name, Class<?>... params) {
            try {
                return type.getMethod(name, params).getDeclaringClass() == NotifiableInventory.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }

        private record Leaf(@Nullable KeyInventory<?> inv, @Nullable NotifiableInventory<?> owner, int size, int direct) {}
    }
}
