package com.gregtechceu.gtceu.api.recipe.content;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;

public final class DefaultedItemBase implements KeyIngredient {

    public final AEItemKey key;

    DefaultedItemBase(AEItemKey key) {
        this.key = key;
    }

    @Override
    public byte kind() {
        return BASE;
    }

    @Override
    public AEKeyType getType() {
        return AEKeyTypes.ITEMS;
    }

    @Override
    public AEItemKey key() {
        return key;
    }

    @Override
    public int uid() {
        return key.uid;
    }

    @Override
    public boolean test(AEKey k) {
        return k.getUid() == key.uid;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DefaultedItemBase o && o.key == key;
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "Base[" + key + "]";
    }
}
