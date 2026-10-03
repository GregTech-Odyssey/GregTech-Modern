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
public final class ForgeItemAdapter implements IItemHandler, ExternalStorageFacade.DirectKeyHandler {

    static final int INSERT = 1;
    static final int EXTRACT = 2;
    static final int LIMIT = 4;
    private static final ItemStack[] NO_VIEWS = new ItemStack[0];
    private static final ItemStack[][] NO_CACHES = new ItemStack[0][];
    private static final ItemViews[] NO_STORES = new ItemViews[0];

    private final IKeyHandler<AEItemKey> handler;
    private final boolean strictSlots;
    private final int size;
    @Nullable
    private final KeyInventory<?> inv0;
    @Nullable
    private final NotifiableInventory<?> owner0;
    private final int direct0;
    private final KeyInventory<?>[] invs;
    private final int[] locals;
    private final byte[] direct;
    private final NotifiableInventory<?>[] owners;
    private final KeyInventory<?>[] distinct;
    private final ItemStack[][] caches;
    private final ItemViews[] stores;
    private ItemStack[] views = NO_VIEWS;
    @Nullable
    private ItemViews store0;
    @Nullable
    private ItemViews local;
    @Nullable
    private AEItemKey item0;
    @Nullable
    private AEItemKey item1;
    @Nullable
    private AEItemKey item2;
    @Nullable
    private AEItemKey item3;
    @Nullable
    private AEItemKey lastTagKey;

    public ForgeItemAdapter(IKeyHandler<AEItemKey> handler) {
        this.handler = handler;
        this.strictSlots = strictSlots(handler);
        var f = Flat.of(handler);
        if (f != null) {
            int n = f.size;
            this.size = n;
            this.inv0 = f.inv0;
            this.invs = f.invs;
            this.locals = f.locals;
            this.direct = f.direct;
            this.owners = f.owners;
            if (f.inv0 != null) {
                this.owner0 = f.owners[0];
                this.direct0 = f.direct[0];
                this.distinct = Flat.NO_INVS;
                this.caches = NO_CACHES;
                this.stores = NO_STORES;
            } else {
                this.owner0 = null;
                this.direct0 = 0;
                this.distinct = Flat.distinct(f.invs);
                this.caches = new ItemStack[n][];
                this.stores = new ItemViews[n];
            }
        } else {
            this.size = -1;
            this.inv0 = null;
            this.owner0 = null;
            this.direct0 = 0;
            this.invs = Flat.NO_INVS;
            this.locals = Flat.NO_LOCALS;
            this.direct = Flat.NO_DIRECT;
            this.owners = Flat.NO_OWNERS;
            this.distinct = Flat.NO_INVS;
            this.caches = NO_CACHES;
            this.stores = NO_STORES;
        }
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

    public IKeyHandler<AEItemKey> getHandler() {
        return handler;
    }

    @Override
    public int getSlots() {
        int n = size;
        return n >= 0 ? n : handler.size();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        var vs = views;
        if (slot >= 0 && slot < vs.length) {
            var v = vs[slot];
            if (v != null) return v;
        } else if (slot >= 0 && slot < caches.length) {
            var c = caches[slot];
            if (c != null) {
                var v = c[locals[slot]];
                if (v != null) return v;
            }
        }
        return refresh(slot);
    }

    private ItemStack refresh(int slot) {
        if (slot >= 0 && slot < size) {
            var inv = inv0;
            int l = slot;
            if (inv == null) {
                inv = invs[slot];
                l = locals[slot];
            }
            if (inv != null) {
                var st = bind(slot, inv);
                st.touch(l);
                long amount = inv.amountAt(l);
                var v = st.view(l, amount == 0 ? null : (AEItemKey) inv.rawKeyAt(l), amount);
                st.cache[l] = v;
                return v;
            }
            var lo = local(size);
            lo.touch(slot);
            return lo.view(slot, handler.keyAt(slot), handler.amountAt(slot));
        }
        if (slot < 0 || size >= 0) {
            var key = handler.keyAt(slot);
            return key == null ? ItemStack.EMPTY : key.toStack(Keys.saturatedInt(handler.amountAt(slot)));
        }
        var key = handler.keyAt(slot);
        var lo = local;
        if (key == null && (lo == null || slot >= lo.lent.length)) return ItemStack.EMPTY;
        lo = local(Math.max(slot + 1, handler.size()));
        lo.touch(slot);
        return lo.view(slot, handler.keyAt(slot), handler.amountAt(slot));
    }

    private ItemViews bind(int slot, KeyInventory<?> inv) {
        var st = inv0 != null ? store0 : stores[slot];
        if (st == null) {
            st = ItemViews.attach(inv);
            bindAll(inv, st);
        }
        return st;
    }

    private @Nullable ItemViews peek(int slot, KeyInventory<?> inv) {
        var st = inv0 != null ? store0 : stores[slot];
        if (st == null && (st = ItemViews.peek(inv)) != null) bindAll(inv, st);
        return st;
    }

    private void bindAll(KeyInventory<?> inv, ItemViews st) {
        if (inv0 != null) {
            store0 = st;
            views = st.cache;
            return;
        }
        var is = invs;
        for (int s = 0; s < is.length; s++) {
            if (is[s] == inv) {
                stores[s] = st;
                caches[s] = st.cache;
            }
        }
    }

    private ItemViews local(int n) {
        var lo = local;
        if (lo == null) local = lo = ItemViews.local(handler.unrestricted(), n);
        else if (n > lo.lent.length) local = lo = lo.grown(n);
        return lo;
    }

    private void touchLocal(int slot) {
        var lo = local;
        if (lo != null && slot >= 0 && slot < lo.lent.length) lo.touch(slot);
    }

    private void touchSlot(int slot) {
        if (slot >= 0 && slot < size) {
            var inv = inv0;
            int l = slot;
            if (inv == null) {
                inv = invs[slot];
                l = locals[slot];
            }
            if (inv != null) {
                var st = peek(slot, inv);
                if (st != null) st.touch(l);
                return;
            }
        }
        touchLocal(slot);
    }

    private void touchKey(AEItemKey key) {
        var inv = inv0;
        if (inv != null) {
            var st = store0;
            if (st == null && (st = ItemViews.peek(inv)) != null) bindAll(inv, st);
            if (st != null) st.touchKey(key);
        } else {
            for (var d : distinct) {
                var st = ItemViews.peek(d);
                if (st != null) st.touchKey(key);
            }
        }
        var lo = local;
        if (lo != null) lo.touchKey(key);
    }

    private @Nullable AEItemKey keyAt(int slot) {
        if (slot >= 0 && slot < invs.length) {
            var inv = inv0;
            int l = slot;
            if (inv == null) {
                inv = invs[slot];
                if (inv == null) return handler.keyAt(slot);
                l = locals[slot];
            }
            return inv.amountAt(l) == 0 ? null : (AEItemKey) inv.rawKeyAt(l);
        }
        return handler.keyAt(slot);
    }

    private long amountAt(int slot) {
        if (slot >= 0 && slot < invs.length) {
            var inv = inv0;
            if (inv != null) return inv.amountAt(slot);
            inv = invs[slot];
            if (inv != null) return inv.amountAt(locals[slot]);
        }
        return handler.amountAt(slot);
    }

    private @Nullable AEItemKey keyOf(ItemStack stack) {
        if (stack.getTag() != null) {
            var k = lastTagKey;
            if (k != null && k.matches(stack)) return k;
            k = Keys.item(stack);
            if (k != null) lastTagKey = k;
            return k;
        }
        var item = stack.getItem();
        var k = item0;
        if (k != null && k.getItem() == item) return k;
        if ((k = item1) != null && k.getItem() == item) return k;
        if ((k = item2) != null && k.getItem() == item) return k;
        if ((k = item3) != null && k.getItem() == item) return k;
        k = Keys.item(stack);
        if (k != null) {
            item3 = item2;
            item2 = item1;
            item1 = item0;
            item0 = k;
        }
        return k;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        int count = stack.getCount();
        if (count <= 0) return stack;
        KeyInventory<?> inv = null;
        int l = slot;
        int d = 0;
        NotifiableInventory<?> o = null;
        if (slot >= 0 && slot < size) {
            inv = inv0;
            if (inv != null) {
                d = direct0;
                o = owner0;
            } else if ((inv = invs[slot]) != null) {
                l = locals[slot];
                d = direct[slot];
                o = owners[slot];
            }
        }
        ItemViews st = null;
        AEItemKey current;
        if (inv != null) {
            st = peek(slot, inv);
            if (st != null) st.touch(l);
            current = inv.amountAt(l) == 0 ? null : (AEItemKey) inv.rawKeyAt(l);
        } else {
            touchLocal(slot);
            current = handler.keyAt(slot);
        }
        AEItemKey key;
        if (current == null) {
            key = keyOf(stack);
        } else if (current.getItem() != stack.getItem()) {
            if (strictSlots) return stack;
            key = keyOf(stack);
        } else {
            var t = stack.getTag();
            var kt = current.getTag();
            key = t == kt || t != null && t.equals(kt) ? current : keyOf(stack);
        }
        if (key == null) return stack;
        long n;
        if (inv == null || (d & INSERT) == 0) {
            n = handler.insert(slot, key, count, simulate);
        } else if (o == null || !o.isVoiding && o.canCapInput()) {
            n = ((KeyInventory<AEItemKey>) inv).insert(l, key, count, simulate);
            if (n > 0 && !simulate && st != null) st.placed(inv, l, key);
        } else {
            n = o.canCapInput() ? handler.insert(slot, key, count, simulate) : 0;
        }
        if (n <= 0) return stack;
        if (n >= count) return ItemStack.EMPTY;
        return stack.copyWithCount(count - (int) n);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        KeyInventory<?> inv = null;
        int l = slot;
        int d = 0;
        NotifiableInventory<?> o = null;
        if (slot >= 0 && slot < size) {
            inv = inv0;
            if (inv != null) {
                d = direct0;
                o = owner0;
            } else if ((inv = invs[slot]) != null) {
                l = locals[slot];
                d = direct[slot];
                o = owners[slot];
            }
        }
        if (inv != null) {
            if (inv.amountAt(l) == 0) return ItemStack.EMPTY;
            var st = peek(slot, inv);
            if (st != null && st.violated(l)) {
                st.settle(l);
                if (inv.amountAt(l) == 0) return ItemStack.EMPTY;
            }
            var key = (AEItemKey) inv.rawKeyAt(l);
            long n;
            if ((d & EXTRACT) == 0) {
                n = handler.extract(slot, key, amount, simulate);
            } else {
                if (o != null && !o.canCapOutput()) return ItemStack.EMPTY;
                n = ((KeyInventory<AEItemKey>) inv).extract(l, key, amount, simulate);
            }
            if (n <= 0) return ItemStack.EMPTY;
            return simulate || st == null ? key.toStack((int) n) : st.taken(inv, l, key, n);
        }
        if (handler.keyAt(slot) == null) return ItemStack.EMPTY;
        touchLocal(slot);
        var key = handler.keyAt(slot);
        if (key == null) return ItemStack.EMPTY;
        long n = handler.extract(slot, key, amount, simulate);
        return n <= 0 ? ItemStack.EMPTY : key.toStack((int) n);
    }

    @Override
    public int getSlotLimit(int slot) {
        if (slot >= 0 && slot < size) {
            var inv = inv0;
            if (inv != null) return Keys.saturatedInt((direct0 & LIMIT) != 0 ? inv.slotLimit() : owner0.slotLimit(slot));
            inv = invs[slot];
            if (inv != null) return Keys.saturatedInt((direct[slot] & LIMIT) != 0 ? inv.slotLimit() : owners[slot].slotLimit(locals[slot]));
        }
        return Keys.saturatedInt(handler.slotLimit(slot));
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        var key = Keys.itemType(stack);
        if (key == null) return false;
        long space;
        var inv = slot >= 0 && slot < invs.length ? invs[slot] : null;
        if (inv != null && (direct[slot] & INSERT) != 0) {
            var o = owners[slot];
            int l = locals[slot];
            space = o == null ? ((KeyInventory<AEItemKey>) inv).spaceFor(l, key) : ((NotifiableInventory<AEItemKey>) o).insert(l, key, Long.MAX_VALUE, true);
        } else {
            space = handler.spaceFor(slot, key);
        }
        return space > 0 || keyAt(slot) == key;
    }

    @Override
    public int getKeySlots() {
        return getSlots();
    }

    @Override
    public @Nullable AEKey getKeyInSlot(int slot) {
        touchSlot(slot);
        return keyAt(slot);
    }

    @Override
    public long getAmountInSlot(int slot) {
        touchSlot(slot);
        return amountAt(slot);
    }

    @Override
    public long insertKey(AEKey what, long amount, Actionable mode) {
        if (!(what instanceof AEItemKey key)) return 0;
        return handler.insert(key, amount, mode == Actionable.SIMULATE);
    }

    @Override
    public long extractKey(AEKey what, long amount, Actionable mode) {
        if (!(what instanceof AEItemKey key)) return 0;
        touchKey(key);
        return handler.extract(key, amount, mode == Actionable.SIMULATE);
    }

    static final class Flat {

        static final KeyInventory<?>[] NO_INVS = new KeyInventory<?>[0];
        static final int[] NO_LOCALS = new int[0];
        static final byte[] NO_DIRECT = new byte[0];
        static final NotifiableInventory<?>[] NO_OWNERS = new NotifiableInventory<?>[0];
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
