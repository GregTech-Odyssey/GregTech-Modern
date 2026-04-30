package com.gto.datasynclib;

import com.gto.datasynclib.field.*;
import com.gto.datasynclib.util.HashUtil;
import com.gto.datasynclib.util.ReflectUtil;
import com.gto.datasynclib.util.cache.HashMapCache;
import com.gto.datasynclib.util.cache.IdentityHashMapCache;
import com.gto.datasynclib.util.cache.MapCache;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;

public final class FieldDefinitionStorage {

    private static final PriorityQueue<DataField.CustomGenericFactory<?>> GENERIC_FIELDS = new PriorityQueue<>(Comparator.comparingInt(f -> -f.priority()));
    private static final MapCache<Class<?>, MapCache<Supplier<Class<?>[]>, DataField.Factory<?>>> GENERIC_FIELDS_CACHE = new IdentityHashMapCache<>(type -> new HashMapCache<>(genericType -> {
        for (var f : GENERIC_FIELDS) {
            if (f.predicate().test(type, genericType.get())) {
                return f.factory().apply(type, genericType.get());
            }
        }
        throw new IllegalArgumentException("No factory for " + type);
    }));

    private static final PriorityQueue<DataField.CustomFactory<?>> FIELDS = new PriorityQueue<>(Comparator.comparingInt(f -> -f.priority()));
    private static final MapCache<Class<?>, DataField.Factory<?>> FIELDS_CACHE = new IdentityHashMapCache<>(type -> {
        for (var f : FIELDS) {
            if (f.predicate().test(type)) {
                return f.factory().apply(type);
            }
        }
        throw new IllegalArgumentException("No factory for " + type);
    });

    private static final PriorityQueue<DataField.CustomFactory<?>> ACCESS = new PriorityQueue<>(Comparator.comparingInt(f -> -f.priority()));
    private static final MapCache<Class<?>, DataField.Factory<?>> ACCESS_CACHE = new IdentityHashMapCache<>(type -> {
        for (var f : ACCESS) {
            if (f.predicate().test(type)) {
                return f.factory().apply(type);
            }
        }
        throw new IllegalArgumentException("No factory for " + type);
    });

    private static final Reference2ReferenceOpenHashMap<Class<?>, Hash.Strategy<?>> STRATEGYS = new Reference2ReferenceOpenHashMap<>();

    private static final Reference2ReferenceOpenHashMap<Class<?>, DataField.Factory<?>> PRIMITIVE_FIELDS = new Reference2ReferenceOpenHashMap<>();

    private static final ConcurrentHashMap<Class<?>, FieldDefinitionStorage> CACHE = new ConcurrentHashMap<>();

    private static final FieldDefinitionStorage EMPTY = new FieldDefinitionStorage();

    public static <T> void registerAccessInterfaceFactory(Class<T> interfaceType, Function<Class<?>, DataField.Factory<T>> factory, int priority) {
        registerAccessCustomFactory(interfaceType::isAssignableFrom, factory, priority);
    }

    public static <T> void registerAccessCustomFactory(Predicate<Class<?>> type, Function<Class<?>, DataField.Factory<T>> factory, int priority) {
        synchronized (ACCESS) {
            ACCESS.add(new DataField.CustomFactory<>(type, factory, priority));
        }
    }

    public static <T> void registerCustomGenericFactory(BiPredicate<Class<?>, Class<?>[]> type, BiFunction<Class<?>, Class<?>[], DataField.Factory<T>> factory, int priority) {
        synchronized (GENERIC_FIELDS) {
            GENERIC_FIELDS.add(new DataField.CustomGenericFactory<>(type, factory, priority));
        }
    }

    public static <T> void registerInterfaceFactory(Class<T> interfaceType, Function<Class<?>, DataField.Factory<T>> factory, int priority) {
        registerCustomFactory(interfaceType::isAssignableFrom, factory, priority);
    }

    public static <T> void registerCustomFactory(Predicate<Class<?>> type, Function<Class<?>, DataField.Factory<T>> factory, int priority) {
        synchronized (FIELDS) {
            FIELDS.add(new DataField.CustomFactory<>(type, factory, priority));
        }
    }

    public static <T> void registerStrategy(Class<T> type, Hash.Strategy<T> strategy) {
        synchronized (STRATEGYS) {
            STRATEGYS.put(type, strategy);
        }
    }

    private static <T> void registerPrimitiveFactory(Class<T> type, DataField.Factory<T> factory) {
        PRIMITIVE_FIELDS.put(type, factory);
    }

    public static DataField.Factory<?> getPrimitiveFactory(Class<?> type) {
        return PRIMITIVE_FIELDS.get(type);
    }

    static {
        registerPrimitiveFactory(boolean.class, BooleanField::new);
        registerPrimitiveFactory(byte.class, ByteField::new);
        registerPrimitiveFactory(char.class, CharField::new);
        registerPrimitiveFactory(double.class, DoubleField::new);
        registerPrimitiveFactory(float.class, FloatField::new);
        registerPrimitiveFactory(int.class, IntField::new);
        registerPrimitiveFactory(long.class, LongField::new);
        registerPrimitiveFactory(short.class, ShortField::new);
    }

    final HashMap<String, DataFieldDefinition<?>> allDefinition;
    final DataFieldDefinition<?>[] saveDefinitions;
    final DataFieldDefinition<?>[] syncToClientDefinitions;
    final DataFieldDefinition<?>[] syncToServerDefinitions;

    private FieldDefinitionStorage(ArrayList<DataFieldDefinition<?>> fields) {
        allDefinition = new HashMap<>(fields.size());
        var saveList = new ArrayList<DataFieldDefinition<?>>(fields.size());
        var syncToClientList = new ArrayList<DataFieldDefinition<?>>(fields.size());
        var syncToServerList = new ArrayList<DataFieldDefinition<?>>(fields.size());
        fields.forEach(d -> {
            if (allDefinition.put(d.key, d) != null) throw new RuntimeException("Duplicate sync field key: " + d.key);
            if (d.isSave) saveList.add(d);
            if (d.isSyncToClient) syncToClientList.add(d);
            if (d.isSyncToServer) syncToServerList.add(d);
        });
        saveList.sort(DataFieldDefinition.COMPARATOR);
        syncToClientList.sort(DataFieldDefinition.COMPARATOR);
        syncToServerList.sort(DataFieldDefinition.COMPARATOR);
        saveDefinitions = saveList.toArray(new DataFieldDefinition[0]);
        syncToClientDefinitions = syncToClientList.toArray(new DataFieldDefinition[0]);
        syncToServerDefinitions = syncToServerList.toArray(new DataFieldDefinition[0]);
    }

    private FieldDefinitionStorage() {
        allDefinition = new HashMap<>(1);
        saveDefinitions = new DataFieldDefinition[0];
        syncToClientDefinitions = new DataFieldDefinition[0];
        syncToServerDefinitions = new DataFieldDefinition[0];
    }

    private static FieldDefinitionStorage of(Class<?> clazz) {
        var definitions = new ArrayList<DataFieldDefinition<?>>();
        scanFields(clazz, definitions, DataFieldDefinition.SOURCE);
        return new FieldDefinitionStorage(definitions);
    }

    private static FieldDefinitionStorage of(Class<?> clazz, FieldDefinitionStorage storage) {
        var definitions = new ArrayList<DataFieldDefinition<?>>();
        scanFields(clazz, definitions, DataFieldDefinition.SOURCE);
        definitions.addAll(storage.allDefinition.values());
        return new FieldDefinitionStorage(definitions);
    }

    public static FieldDefinitionStorage get(Class<?> clazz) {
        var storage = CACHE.get(clazz);
        if (storage != null) return storage;
        synchronized (CACHE) {
            Class<?> sc = clazz.getSuperclass();
            if (sc != null && sc != Object.class) {
                var sh = get(sc);
                storage = of(clazz, sh);
                if (storage.allDefinition.size() == sh.allDefinition.size()) {
                    storage = sh;
                }
            }
            if (storage == null) {
                storage = of(clazz);
                if (storage.allDefinition.isEmpty()) storage = EMPTY;
            }
            CACHE.put(clazz, storage);
        }
        return storage;
    }

    private static void scanFields(Class<?> clazz, ArrayList<DataFieldDefinition<?>> definitions, Function<Object, Object> source) {
        for (var field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            var annotations = new FieldAnnotations(clazz, field);
            if (annotations.isEmpty()) continue;
            var definition = createFieldDefinition(field, source, annotations);
            if (definition != null) definitions.add(definition);
        }
    }

    private static DataFieldDefinition<?> createFieldDefinition(Field field, Function<Object, Object> source, FieldAnnotations annotations) {
        try {
            Class<?>[] genericType;
            field.setAccessible(true);
            try {
                genericType = ReflectUtil.getFieldGenericTypeClasses(field.getGenericType());
            } catch (Throwable e) {
                genericType = new Class<?>[0];
            }
            boolean isFinal = Modifier.isFinal(field.getModifiers());
            if (annotations.generic()) {
                if (genericType.length == 0) throw new IllegalStateException("Field " + field.getName() + " is annotated with @Generic, but it has no generic type");
                return createGenericFieldDefinition(field, source, annotations, genericType, isFinal);
            } else {
                if (annotations.access() || isFinal) {
                    return createFinalFieldDefinition(field, source, annotations, genericType, isFinal);
                } else {
                    return createNonFinalFieldDefinition(field, source, annotations, genericType, isFinal);
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create field definition for: " + field.getName(), e);
        }
    }

    private static DataFieldDefinition<?> createFinalFieldDefinition(Field field, Function<Object, Object> source, FieldAnnotations annotations, Class<?>[] genericType, boolean isFinal) {
        var factory = ACCESS_CACHE.getCache(field.getType());
        return new DataFieldDefinition<>(field, factory, source, annotations, genericType, isFinal, STRATEGYS);
    }

    private static DataFieldDefinition<?> createGenericFieldDefinition(Field field, Function<Object, Object> source, FieldAnnotations annotations, Class<?>[] genericType, boolean isFinal) {
        var factory = GENERIC_FIELDS_CACHE.getCache(field.getType()).getCache(HashUtil.arrayIdentityWrapper(genericType));
        return new DataFieldDefinition<>(field, factory, source, annotations, genericType, isFinal, STRATEGYS);
    }

    private static DataFieldDefinition<?> createNonFinalFieldDefinition(Field field, Function<Object, Object> source, FieldAnnotations annotations, Class<?>[] genericType, boolean isFinal) {
        var type = field.getType();
        try {
            var factory = FIELDS_CACHE.getCache(type);
            return new DataFieldDefinition<>(field, factory, source, annotations, genericType, isFinal, STRATEGYS);
        } catch (Throwable e) {
            try {
                if (genericType.length > 0) {
                    return createGenericFieldDefinition(field, source, annotations, genericType, isFinal);
                }
                throw new RuntimeException(e);
            } catch (Throwable e2) {
                return createFinalFieldDefinition(field, source, annotations, genericType, isFinal);
            }
        }
    }
}
