package com.gto.datasynclib;

import it.unimi.dsi.fastutil.Hash;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;

public final class DataFieldDefinition<T> {

    public static final Hash.Strategy OBJECT_STRATEGY = new Hash.Strategy<>() {

        @Override
        public int hashCode(Object o) {
            return o == null ? 0 : o.hashCode();
        }

        @SuppressWarnings("EqualsReplaceableByObjectsCall")
        @Override
        public boolean equals(Object a, Object b) {
            return (a == b) || (a != null && a.equals(b));
        }
    };

    static Function<Object, Object> SOURCE = Function.identity();

    static final Comparator<DataFieldDefinition<?>> COMPARATOR = Comparator.comparing(d -> d.key);

    public final Field field;
    private final Method clientUpdateListener;
    private final Method serverUpdateListener;
    public final boolean isFinal;
    public final Hash.Strategy<T> strategy;
    public final CombinationCodec<T> codec;
    public final Class<?>[] genericType;
    public final CombinationCodec<?>[] genericCodec;
    public final DataField.Factory<T> factory;
    public final Function<Object, Object> source;

    public final boolean isSave;
    public final String key;

    public final boolean isSyncToClient;
    public final boolean isSyncToServer;

    private final boolean notifyClientUpdate;
    private final boolean notifyServerUpdate;
    private final boolean autoServerUpdate;
    private final boolean autoClientUpdate;

    @SuppressWarnings("unchecked")
    DataFieldDefinition(Field field, DataField.Factory<T> factory, Function<Object, Object> source, FieldAnnotations fieldAnnotations, Class<?>[] genericType, boolean isFinal, boolean access, Map<Class<?>, Hash.Strategy<?>> strategys) {
        this.field = field;
        this.factory = factory;
        this.source = source;
        this.key = fieldAnnotations.key();
        this.isSave = fieldAnnotations.isSave();
        this.isSyncToClient = fieldAnnotations.isSyncToClient();
        this.isSyncToServer = fieldAnnotations.isSyncToServer();
        this.notifyClientUpdate = fieldAnnotations.notifyClientUpdate();
        this.notifyServerUpdate = fieldAnnotations.notifyServerUpdate();
        this.autoServerUpdate = fieldAnnotations.autoServerUpdate();
        this.autoClientUpdate = fieldAnnotations.autoClientUpdate();
        this.clientUpdateListener = fieldAnnotations.clientUpdateListener();
        this.serverUpdateListener = fieldAnnotations.serverUpdateListener();
        this.codec = fieldAnnotations.dataCodec() != null ? new CombinationCodec<>(fieldAnnotations.streamCodec(), fieldAnnotations.dataCodec()) : access ? null : (CombinationCodec<T>) CombinationCodec.get(field.getType());
        this.genericType = genericType;
        this.genericCodec = new CombinationCodec[genericType.length];
        this.isFinal = isFinal;
        this.strategy = fieldAnnotations.strategy() != null ? fieldAnnotations.strategy() : (Hash.Strategy<T>) strategys.getOrDefault(field.getType(), OBJECT_STRATEGY);
        for (int i = 0; i < genericType.length; i++) {
            this.genericCodec[i] = CombinationCodec.get(genericType[i]);
        }
    }

    public Method getListener(LogicalSide side) {
        return side == LogicalSide.CLIENT ? clientUpdateListener : serverUpdateListener;
    }

    boolean notifyUpdate(LogicalSide side) {
        return side == LogicalSide.CLIENT ? notifyClientUpdate : notifyServerUpdate;
    }

    boolean autoUpdate(LogicalSide side) {
        return side == LogicalSide.CLIENT ? autoClientUpdate : autoServerUpdate;
    }
}
