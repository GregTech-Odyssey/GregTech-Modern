package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.transfer.key.Keys;

import appeng.api.stacks.AEKey;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.datastream.data.ListData;
import com.gto.fastcollection.cache.CustomHashInterner;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 不可变的配方内容表（按声明顺序保存，SoA），数量为单份数量，运行期倍率由 GTRecipe.scale 提供；
 * 默认概率、加成、规划顺序与掷骰单位数组在相同长度间共享，可能重叠的消耗型条目分组驻留在规划顺序数组尾部。
 */
public final class ContentList {

    public static final int MAX_CHANCE = 10_000;
    private static final int SHARED = 64;
    private static final int[][] FULL_CHANCES = new int[SHARED][];
    private static final int[][] ZERO_BOOSTS = new int[SHARED][];
    private static final int[][] IDENTITY = new int[SHARED][];

    static {
        for (int n = 0; n < SHARED; n++) {
            var c = new int[n];
            Arrays.fill(c, MAX_CHANCE);
            FULL_CHANCES[n] = c;
            ZERO_BOOSTS[n] = new int[n];
            var o = new int[n];
            for (int i = 0; i < n; i++) o[i] = i;
            IDENTITY[n] = o;
        }
    }

    private static final byte ACTIVE_OUTPUT = 1, DISTINCT_OUTPUT_KEYS = 2, OVERLAP = 4;
    private static final int MAX_OVERLAP = 64;
    private static final CustomHashInterner<int[]> LAYOUTS = new CustomHashInterner<>(IntArrays.HASH_STRATEGY);

    public static final ContentList EMPTY = new ContentList(new KeyIngredient[0], new long[0], FULL_CHANCES[0], ZERO_BOOSTS[0], new long[0], IDENTITY[0], (byte) 0);

    final KeyIngredient[] ingredients;
    final long[] amounts;
    final int[] chances;
    final int[] boosts;
    final long[] rollUnits;
    final int[] planOrder;
    final long maxScalable;
    private final byte flags;

    private ContentList(KeyIngredient[] ingredients, long[] amounts, int[] chances, int[] boosts, long[] rollUnits, int[] planOrder, byte flags) {
        this.ingredients = ingredients;
        this.amounts = amounts;
        this.chances = chances;
        this.boosts = boosts;
        this.rollUnits = rollUnits;
        this.planOrder = planOrder;
        this.flags = flags;
        int n = ingredients.length;
        long max = 0;
        for (int i = 0; i < n; i++) {
            if (chances[i] > 0 && amounts[i] > max) max = amounts[i];
        }
        this.maxScalable = max;
    }

    private static byte outputShape(KeyIngredient[] ingredients, long[] amounts, int[] chances) {
        int n = ingredients.length;
        boolean active = false;
        for (int i = 0; i < n; i++) {
            if (chances[i] == 0 || amounts[i] <= 0) continue;
            active = true;
            var key = ingredients[i].key();
            if (key == null) return ACTIVE_OUTPUT;
            for (int j = 0; j < i; j++) {
                if (chances[j] != 0 && amounts[j] > 0 && ingredients[j].key() == key) return ACTIVE_OUTPUT;
            }
        }
        return active ? (byte) (ACTIVE_OUTPUT | DISTINCT_OUTPUT_KEYS) : 0;
    }

    private static int[] order(KeyIngredient[] ingredients) {
        int n = ingredients.length;
        boolean identity = true;
        int seenLoose = -1;
        for (int i = 0; i < n; i++) {
            int rank = rank(ingredients[i].kind());
            if (rank < seenLoose) {
                identity = false;
                break;
            }
            seenLoose = Math.max(seenLoose, rank);
        }
        if (identity) return n < SHARED ? IDENTITY[n] : identityOf(n);
        int[] o = new int[n];
        int p = 0;
        for (int r = 0; r <= 2; r++) {
            for (int i = 0; i < n; i++) {
                if (rank(ingredients[i].kind()) == r) o[p++] = i;
            }
        }
        return o;
    }

    private static int rank(byte kind) {
        return switch (kind) {
            case KeyIngredient.TAG -> 1;
            case KeyIngredient.PREDICATE -> 2;
            default -> 0;
        };
    }

    private static int[] plan(KeyIngredient[] ingredients, int[] chances) {
        int n = ingredients.length;
        int[] root = null;
        for (int i = 1; i < n; i++) {
            if (chances[i] == 0) continue;
            var a = ingredients[i];
            for (int j = 0; j < i; j++) {
                if (chances[j] == 0) continue;
                if (root != null && find(root, i) == find(root, j)) continue;
                if (!mayOverlap(a, ingredients[j])) continue;
                if (root == null) root = identityOf(n);
                root[find(root, i)] = find(root, j);
            }
        }
        return root == null ? order(ingredients) : layout(ingredients, root);
    }

    private static int find(int[] root, int i) {
        while (root[i] != i) {
            root[i] = root[root[i]];
            i = root[i];
        }
        return i;
    }

    private static boolean mayOverlap(KeyIngredient a, KeyIngredient b) {
        if (a.getType() != b.getType()) return false;
        if (!concrete(a) || !concrete(b)) return true;
        boolean ca = a instanceof CircuitIngredient, cb = b instanceof CircuitIngredient;
        if (ca || cb) return a == b || ca != cb;
        var ka = a.key();
        var kb = b.key();
        if (a.kind() == KeyIngredient.EXACT && b.kind() == KeyIngredient.EXACT) return ka == kb;
        return ka.getUid() == kb.getUid();
    }

    private static boolean concrete(KeyIngredient ing) {
        Object o = ing;
        return o instanceof AEKey || o instanceof DefaultedItemBase || o instanceof CircuitIngredient;
    }

    private static int specificity(KeyIngredient ing) {
        if (ing instanceof FluidTagNbtIngredient) return 1;
        return switch (ing.kind()) {
            case KeyIngredient.EXACT, KeyIngredient.CIRCUIT -> 0;
            case KeyIngredient.BASE -> 2;
            case KeyIngredient.TAG -> 3;
            default -> 4;
        };
    }

    private static int[] layout(KeyIngredient[] ingredients, int[] root) {
        int n = ingredients.length;
        int[] layout = new int[2 * n + 1];
        int p = 0;
        for (int r = 0; r <= 4; r++) {
            for (int i = 0; i < n; i++) {
                if (specificity(ingredients[i]) == r) layout[p++] = i;
            }
        }
        int[] size = new int[n];
        for (int i = 0; i < n; i++) size[find(root, i)]++;
        boolean[] grouped = new boolean[n];
        int k = 0;
        for (int i = 0; i < n && k < MAX_OVERLAP; i++) {
            if (size[find(root, i)] < 2) continue;
            grouped[i] = true;
            layout[n + 1 + k++] = i;
        }
        layout[n] = k;
        p = n + 1 + k;
        for (int i = 0; i < n; i++) {
            if (!grouped[i]) layout[p++] = i;
        }
        return LAYOUTS.intern(layout);
    }

    private static int[] identityOf(int n) {
        int[] o = new int[n];
        for (int i = 0; i < n; i++) o[i] = i;
        return o;
    }

    private static int[] fullChances(int n) {
        if (n < SHARED) return FULL_CHANCES[n];
        var c = new int[n];
        Arrays.fill(c, MAX_CHANCE);
        return c;
    }

    private static int[] zeroBoosts(int n) {
        return n < SHARED ? ZERO_BOOSTS[n] : new int[n];
    }

    static ContentList of(KeyIngredient[] ingredients, long[] amounts, int[] chances, int[] boosts, @Nullable long[] rollUnits) {
        int n = ingredients.length;
        if (n == 0) return EMPTY;
        boolean full = true, zero = true;
        for (int i = 0; i < n; i++) {
            if (chances[i] != MAX_CHANCE) full = false;
            if (boosts[i] != 0) zero = false;
        }
        int[] plan = plan(ingredients, chances);
        byte flags = outputShape(ingredients, amounts, chances);
        if (plan.length > n) flags |= OVERLAP;
        return new ContentList(ingredients, amounts, full ? fullChances(n) : chances, zero ? zeroBoosts(n) : boosts, rollUnits == null || Arrays.equals(rollUnits, amounts) ? amounts : rollUnits, plan, flags);
    }

    public int size() {
        return ingredients.length;
    }

    public boolean isEmpty() {
        return ingredients.length == 0;
    }

    public KeyIngredient ingredient(int i) {
        return ingredients[i];
    }

    public long amount(int i) {
        return amounts[i];
    }

    public int chance(int i) {
        return chances[i];
    }

    public int boost(int i) {
        return boosts[i];
    }

    public long rollUnit(int i) {
        return rollUnits[i];
    }

    public boolean isConsumable(int i) {
        return chances[i] != 0;
    }

    public AEKey outputKey(int i) {
        var ing = ingredients[i];
        return ing instanceof AEKey k ? k : ing.outputKey();
    }

    public boolean hasActiveOutput() {
        return (flags & ACTIVE_OUTPUT) != 0;
    }

    public boolean distinctOutputKeys() {
        return (flags & DISTINCT_OUTPUT_KEYS) != 0;
    }

    public int[] planOrder() {
        return planOrder;
    }

    public boolean hasOverlap() {
        return (flags & OVERLAP) != 0;
    }

    public int overlapCount() {
        return planOrder[ingredients.length];
    }

    public int overlapEntry(int j) {
        return planOrder[ingredients.length + 1 + j];
    }

    public int separateEntry(int j) {
        int n = ingredients.length;
        return planOrder[n + 1 + planOrder[n] + j];
    }

    public long maxScalable() {
        return maxScalable;
    }

    public long effective(int i, long scale) {
        long a = amounts[i];
        return chances[i] == 0 || scale == 1 ? a : Keys.multiply(a, scale);
    }

    public ContentList scaled(long scale) {
        if (scale == 1 || isEmpty()) return this;
        int n = ingredients.length;
        long[] a = new long[n];
        for (int i = 0; i < n; i++) a[i] = effective(i, scale);
        return new ContentList(ingredients, a, chances, boosts, rollUnits, planOrder, flags);
    }

    public ContentList withAmount(int i, long amount) {
        long[] a = amounts.clone();
        a[i] = amount;
        byte shape = (byte) (outputShape(ingredients, a, chances) | flags & OVERLAP);
        if (rollUnits == amounts) return new ContentList(ingredients, a, chances, boosts, a, planOrder, shape);
        long old = amounts[i];
        long unit = rollUnits[i];
        long scaled = unit == old ? amount : old > 0 && amount % old == 0 ? Keys.multiply(unit, amount / old) : unit;
        if (scaled == unit) return new ContentList(ingredients, a, chances, boosts, rollUnits, planOrder, shape);
        long[] u = rollUnits.clone();
        u[i] = scaled;
        return new ContentList(ingredients, a, chances, boosts, u, planOrder, shape);
    }

    public ContentList trimFirst(int n) {
        if (n < 0 || n >= size()) return this;
        return range(0, n);
    }

    public ContentList trimLast(int n) {
        int size = size();
        if (n < 0 || n >= size) return this;
        return range(size - n, size);
    }

    public ContentList range(int from, int to) {
        int size = size();
        if (from < 0) from = 0;
        if (to > size) to = size;
        if (from >= to) return EMPTY;
        if (from == 0 && to == size) return this;
        return of(Arrays.copyOfRange(ingredients, from, to), Arrays.copyOfRange(amounts, from, to), Arrays.copyOfRange(chances, from, to), Arrays.copyOfRange(boosts, from, to), Arrays.copyOfRange(rollUnits, from, to));
    }

    public static ContentList concat(ContentList a, ContentList b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        var builder = new Builder(a.size() + b.size());
        builder.addAll(a);
        builder.addAll(b);
        return builder.build();
    }

    public Builder toBuilder() {
        var b = new Builder(size());
        b.addAll(this);
        return b;
    }

    public List<KeyIngredient> ingredients() {
        return Arrays.asList(ingredients);
    }

    public Data toData() {
        int n = size();
        var list = new ListData(n * 5);
        for (int i = 0; i < n; i++) {
            list.add(ingredients[i].toData());
            list.addLong(amounts[i]);
            list.addInt(chances[i]);
            list.addInt(boosts[i]);
            list.addLong(rollUnits[i]);
        }
        return list;
    }

    public static ContentList fromData(Data data, int dataVersion) {
        var list = data.getList();
        int n = list.size() / 5;
        var b = new Builder(n);
        for (int i = 0; i < n; i++) {
            int o = i * 5;
            var ingredient = KeyIngredient.fromData(list.get(o), dataVersion);
            if (ingredient != null) b.add(ingredient, list.get(o + 1).getLong(), list.get(o + 2).getInt(), list.get(o + 3).getInt(), list.get(o + 4).getLong());
        }
        return b.build();
    }

    public void toNetwork(net.minecraft.network.FriendlyByteBuf buf) {
        int n = size();
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            ingredients[i].toNetwork(buf);
            buf.writeVarLong(amounts[i]);
            buf.writeVarInt(chances[i]);
            buf.writeVarInt(boosts[i]);
            buf.writeVarLong(rollUnits[i]);
        }
    }

    public static ContentList fromNetwork(net.minecraft.network.FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > 4096) throw new IllegalArgumentException("Invalid content list size " + n);
        var b = new Builder(n);
        for (int i = 0; i < n; i++) {
            b.add(KeyIngredient.fromNetwork(buf), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong());
        }
        return b.build();
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("[");
        for (int i = 0; i < size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(amounts[i]).append("x ").append(ingredients[i]);
            if (chances[i] != MAX_CHANCE) sb.append(" @").append(chances[i]);
        }
        return sb.append(']').toString();
    }

    public static final class Builder {

        private final ArrayList<KeyIngredient> ingredients;
        private long[] amounts;
        private int[] chances;
        private int[] boosts;
        private long[] rollUnits;

        public Builder() {
            this(4);
        }

        public Builder(int capacity) {
            capacity = Math.max(capacity, 1);
            ingredients = new ArrayList<>(capacity);
            amounts = new long[capacity];
            chances = new int[capacity];
            boosts = new int[capacity];
            rollUnits = new long[capacity];
        }

        public int size() {
            return ingredients.size();
        }

        public boolean isEmpty() {
            return ingredients.isEmpty();
        }

        public Builder add(KeyIngredient ingredient, long amount) {
            return add(ingredient, amount, MAX_CHANCE, 0, amount);
        }

        public Builder add(KeyIngredient ingredient, long amount, int chance, int boost) {
            return add(ingredient, amount, chance, boost, amount);
        }

        public Builder add(KeyIngredient ingredient, long amount, int chance, int boost, long rollUnit) {
            int n = ingredients.size();
            if (n == amounts.length) {
                int c = n * 2;
                amounts = Arrays.copyOf(amounts, c);
                chances = Arrays.copyOf(chances, c);
                boosts = Arrays.copyOf(boosts, c);
                rollUnits = Arrays.copyOf(rollUnits, c);
            }
            ingredients.add(ingredient);
            amounts[n] = amount;
            chances[n] = chance;
            boosts[n] = boost;
            rollUnits[n] = rollUnit <= 0 ? amount : rollUnit;
            return this;
        }

        public Builder addAll(ContentList list) {
            for (int i = 0; i < list.size(); i++) {
                add(list.ingredients[i], list.amounts[i], list.chances[i], list.boosts[i], list.rollUnits[i]);
            }
            return this;
        }

        public KeyIngredient ingredient(int i) {
            return ingredients.get(i);
        }

        public long amount(int i) {
            return amounts[i];
        }

        public int chance(int i) {
            return chances[i];
        }

        public int boost(int i) {
            return boosts[i];
        }

        public Builder set(int i, KeyIngredient ingredient, long amount, int chance, int boost) {
            ingredients.set(i, ingredient);
            amounts[i] = amount;
            chances[i] = chance;
            boosts[i] = boost;
            rollUnits[i] = amount;
            return this;
        }

        public Builder remove(int i) {
            int n = ingredients.size();
            ingredients.remove(i);
            System.arraycopy(amounts, i + 1, amounts, i, n - i - 1);
            System.arraycopy(chances, i + 1, chances, i, n - i - 1);
            System.arraycopy(boosts, i + 1, boosts, i, n - i - 1);
            System.arraycopy(rollUnits, i + 1, rollUnits, i, n - i - 1);
            return this;
        }

        public Builder clear() {
            ingredients.clear();
            return this;
        }

        public Builder copy() {
            var b = new Builder(Math.max(1, size()));
            for (int i = 0; i < size(); i++) b.add(ingredients.get(i), amounts[i], chances[i], boosts[i], rollUnits[i]);
            return b;
        }

        public ContentList build() {
            int n = ingredients.size();
            if (n == 0) return EMPTY;
            return of(ingredients.toArray(new KeyIngredient[0]), Arrays.copyOf(amounts, n), Arrays.copyOf(chances, n), Arrays.copyOf(boosts, n), Arrays.copyOf(rollUnits, n));
        }
    }
}
