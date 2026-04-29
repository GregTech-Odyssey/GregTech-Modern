package com.gto.datasynclib;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.function.Function;

public final class DataFieldDefinition<T> {

    static Function<Object, Object> SOURCE = Function.identity();

    static final Comparator<DataFieldDefinition<?>> COMPARATOR = Comparator.comparing(d -> d.key);

    public final Field field;
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
    DataFieldDefinition(Field field, DataField.Factory<T> factory, Function<Object, Object> source, FieldAnnotations fieldAnnotations, Class<?>[] genericType) {
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
        this.codec = (CombinationCodec<T>) CombinationCodec.get(field.getType());
        this.genericType = genericType;
        this.genericCodec = new CombinationCodec[genericType.length];
        for (int i = 0; i < genericType.length; i++) {
            this.genericCodec[i] = CombinationCodec.get(genericType[i]);
        }
    }

    boolean notifyUpdate(LogicalSide side) {
        return side == LogicalSide.CLIENT ? notifyClientUpdate : notifyServerUpdate;
    }

    boolean autoUpdate(LogicalSide side) {
        return side == LogicalSide.CLIENT ? autoClientUpdate : autoServerUpdate;
    }
}
