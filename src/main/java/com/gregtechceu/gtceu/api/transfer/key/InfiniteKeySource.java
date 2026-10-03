package com.gregtechceu.gtceu.api.transfer.key;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import org.jetbrains.annotations.Nullable;

/**
 * 无限源：每槽一个配置 key 与对外显示数量，取出不减少；插入按 {@code acceptInsert} 吞掉或拒收。
 * 创造箱/罐、创造输入总线/仓、无限水仓等共用。
 */
public final class InfiniteKeySource<K extends AEKey> implements IKeyHandler<K> {

    private final KeyInventory<K> config;
    private final boolean acceptInsert;

    public InfiniteKeySource(KeyInventory<K> config, boolean acceptInsert) {
        this.config = config;
        this.acceptInsert = acceptInsert;
    }

    public KeyInventory<K> config() {
        return config;
    }

    @Override
    public AEKeyType keyType() {
        return config.keyType();
    }

    @Override
    public int size() {
        return config.size();
    }

    @Override
    public boolean fixedSize() {
        return config.fixedSize();
    }

    @Override
    public @Nullable K keyAt(int slot) {
        return config.keyAt(slot);
    }

    @Override
    public long amountAt(int slot) {
        return config.amountAt(slot);
    }

    @Override
    public long slotLimit(int slot) {
        return Long.MAX_VALUE;
    }

    @Override
    public long insert(int slot, K key, long amount, boolean simulate) {
        return acceptInsert && amount > 0 ? amount : 0;
    }

    @Override
    public long extract(int slot, K key, long amount, boolean simulate) {
        if (amount <= 0 || config.amountAt(slot) <= 0 || config.rawKeyAt(slot) != key) return 0;
        return amount;
    }

    @Override
    public long insert(K key, long amount, boolean simulate) {
        return acceptInsert && amount > 0 ? amount : 0;
    }

    @Override
    public long extract(K key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        for (int i = 0; i < config.size(); i++) {
            if (config.amountAt(i) > 0 && config.rawKeyAt(i) == key) return amount;
        }
        return 0;
    }

    @Override
    public long count(K key) {
        for (int i = 0; i < config.size(); i++) {
            if (config.amountAt(i) > 0 && config.rawKeyAt(i) == key) return Long.MAX_VALUE;
        }
        return 0;
    }
}
