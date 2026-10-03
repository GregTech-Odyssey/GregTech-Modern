package com.gregtechceu.gtceu.api.transfer.key;

import appeng.api.stacks.AEKey;
import appeng.api.storage.AEKeyFilter;
import org.jetbrains.annotations.Nullable;

public final class KeyIOView<K extends AEKey> extends KeyHandlerView<K> {

    @Nullable
    private final AEKeyFilter inFilter;
    @Nullable
    private final AEKeyFilter outFilter;

    public KeyIOView(IKeyHandler<K> delegate, boolean allowInsert, boolean allowExtract, @Nullable AEKeyFilter inFilter, @Nullable AEKeyFilter outFilter) {
        super(delegate, allowInsert, allowExtract);
        this.inFilter = inFilter;
        this.outFilter = outFilter;
    }

    public @Nullable AEKeyFilter getInFilter() {
        return inFilter;
    }

    public @Nullable AEKeyFilter getOutFilter() {
        return outFilter;
    }

    @Override
    protected boolean canInsert(K key) {
        return super.canInsert(key) && (inFilter == null || inFilter.matches(key));
    }

    @Override
    protected boolean canExtract(K key) {
        return super.canExtract(key) && (outFilter == null || outFilter.matches(key));
    }
}
