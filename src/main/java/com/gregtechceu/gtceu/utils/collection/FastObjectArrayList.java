package com.gregtechceu.gtceu.utils.collection;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class FastObjectArrayList<K> extends ObjectArrayList<K> {

    public K[] getArray() {
        return this.a;
    }

    public void fastRemove(int index) {
        --this.size;
        if (index != this.size) {
            System.arraycopy(this.a, index + 1, this.a, index, this.size - index);
        }
        this.a[this.size] = null;
    }
}
