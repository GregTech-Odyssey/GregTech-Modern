package com.gto.datasynclib;

public final class LazyFieldDataManager {

    private final IFieldDataHolder holder;

    private volatile FieldDataManager fieldDataManager;

    public LazyFieldDataManager(IFieldDataHolder holder) {
        this.holder = holder;
    }

    public FieldDataManager get() {
        var manager = fieldDataManager;
        if (manager == null) {
            synchronized (this) {
                if (fieldDataManager == null) {
                    fieldDataManager = new FieldDataManager(holder);
                }
                manager = fieldDataManager;
            }
        }
        return manager;
    }
}
