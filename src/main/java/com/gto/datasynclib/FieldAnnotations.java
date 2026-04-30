package com.gto.datasynclib;

import com.gto.datasynclib.annotations.*;
import it.unimi.dsi.fastutil.Hash;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.lang.reflect.Field;

@Getter
@Accessors(fluent = true)
final class FieldAnnotations {

    private final SaveToDisk saveToNBT;
    private final SyncToClient syncToClient;
    private final SyncToServer syncToServer;

    private final String key;
    private final Hash.Strategy strategy;
    private final boolean notifyClientUpdate;
    private final boolean notifyServerUpdate;
    private final boolean autoServerUpdate;
    private final boolean autoClientUpdate;
    private final boolean generic;
    private final boolean access;

    FieldAnnotations(Class<?> clazz, Field field) {
        this.generic = field.getAnnotation(Generic.class) != null;
        this.access = field.getAnnotation(Access.class) != null;
        this.saveToNBT = field.getAnnotation(SaveToDisk.class);
        this.syncToClient = field.getAnnotation(SyncToClient.class);
        this.syncToServer = field.getAnnotation(SyncToServer.class);
        this.key = saveToNBT == null || saveToNBT.key().isEmpty() ? field.getName() : saveToNBT.key();
        this.notifyClientUpdate = syncToClient != null && syncToClient.notifyUpdate();
        this.notifyServerUpdate = syncToServer != null && syncToServer.notifyUpdate();
        this.autoServerUpdate = syncToClient != null && syncToClient.autoUpdate();
        this.autoClientUpdate = syncToServer != null && syncToServer.autoUpdate();
        var strategy = field.getAnnotation(Strategy.class);
        if (strategy == null) {
            this.strategy = null;
        } else {
            try {
                var f = clazz.getDeclaredField(strategy.value());
                f.setAccessible(true);
                this.strategy = (Hash.Strategy) f.get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    boolean isEmpty() {
        return saveToNBT == null && syncToClient == null && syncToServer == null;
    }

    boolean isSave() {
        return saveToNBT != null;
    }

    boolean isSyncToClient() {
        return syncToClient != null;
    }

    boolean isSyncToServer() {
        return syncToServer != null;
    }
}
