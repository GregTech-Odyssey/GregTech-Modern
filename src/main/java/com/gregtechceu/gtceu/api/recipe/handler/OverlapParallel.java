package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import java.util.Arrays;

/**
 * 输入并行中可能重叠的消耗型条目：数组成员按槽、自定义成员经 forEachKey 求命中掩码后交给 {@link OverlapBound}；
 * 自定义成员的遍历结果与其 available 逐条目对不上时，按各条目互不重叠计入（与单条目计数一致）。
 */
final class OverlapParallel extends OverlapBound implements IRecipeHandler.KeyVisitor {

    private final KeyIngredient[] ingredients = new KeyIngredient[64];
    private final long[] avail = new long[64];
    private long active;
    private AEKeyType type;

    long bound(ContentList list, IRecipeHandler[] members, KeyInventory<?>[] stores, AEKeyType type, long scale, long par) {
        int count = list.overlapCount();
        var ings = ingredients;
        long active = 0;
        for (int j = 0; j < count; j++) {
            int e = list.overlapEntry(j);
            long nd = list.effective(e, scale);
            need[j] = nd;
            ings[j] = list.ingredient(e);
            if (nd > 0) active |= 1L << j;
        }
        if (active == 0) {
            Arrays.fill(ings, 0, count, null);
            return par;
        }
        this.active = active;
        this.type = type;
        reset(active);
        try {
            for (int h = 0, n = members.length; h < n; h++) {
                var m = members[h];
                if (m.isNotConsumable()) continue;
                var inv = stores[h];
                if (inv != null) scan(inv, active);
                else scanCustom(m, type, active);
            }
        } finally {
            Arrays.fill(ings, 0, count, null);
            this.type = null;
        }
        return solve(active, par);
    }

    private void scan(KeyInventory<?> inv, long active) {
        for (int s = 0, n = inv.size(); s < n; s++) {
            long a = inv.amountAt(s);
            if (a <= 0) continue;
            long mask = maskOf(inv.uidAt(s), inv.rawKeyAt(s), active);
            if (mask != 0) put(mask, a);
        }
    }

    private void scanCustom(IRecipeHandler m, AEKeyType type, long active) {
        var ings = ingredients;
        long[] av = avail;
        boolean any = false;
        for (long r = active; r != 0; r &= r - 1) {
            int j = Long.numberOfTrailingZeros(r);
            long a = m.available(type, ings[j]);
            av[j] = a;
            if (a > 0) any = true;
        }
        if (!any) return;
        beginSegment(active);
        m.forEachKey(type, this);
        closeSegment(active, av);
    }

    private long maskOf(int uid, AEKey key, long active) {
        var ings = ingredients;
        long mask = 0;
        for (long r = active; r != 0; r &= r - 1) {
            if (KeyIngredient.accepts(ings[Long.numberOfTrailingZeros(r)], uid, key)) mask |= r & -r;
        }
        return mask;
    }

    @Override
    public boolean visit(AEKey key, long amount) {
        if (amount > 0 && key.getType() == type) {
            long mask = maskOf(key.getUid(), key, active);
            if (mask != 0) putSegment(mask, amount);
        }
        return false;
    }
}
