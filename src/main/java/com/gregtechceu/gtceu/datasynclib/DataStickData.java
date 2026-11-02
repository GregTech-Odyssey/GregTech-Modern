package com.gregtechceu.gtceu.datasynclib;

import com.gregtechceu.gtceu.api.item.IDataComponentItem;
import com.gregtechceu.gtceu.api.item.ItemDataComponentKey;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.annotation.SaveToDataStick;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.DataField;
import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.DataSyncCodec;
import com.gto.datasynclib.FieldDataManager;
import com.gto.datasynclib.FieldDefinitionStorage;
import com.gto.datasynclib.FieldDefinitionStorage.AnnotatedField;
import com.gto.datasynclib.datastream.codec.JavaValueOps;
import com.gto.datasynclib.datastream.codec.ValueCodec;
import com.gto.datasynclib.util.ReflectUtil;
import org.apache.commons.lang3.ClassUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Data-stick configuration storage; field discovery and codec resolution belong to DataSyncLib. */
public final class DataStickData {

    private static final JavaValueOps OPS = JavaValueOps.create(GTDataFixer.VERSION);
    private static final ClassValue<AnnotatedField<SaveToDataStick>[]> FIELDS = new ClassValue<>() {

        @Override
        protected AnnotatedField<SaveToDataStick>[] computeValue(Class<?> type) {
            var fields = FieldDefinitionStorage.get(type).getAnnotatedFields(SaveToDataStick.class).clone();
            Arrays.sort(fields, Comparator.<AnnotatedField<SaveToDataStick>>comparingInt(f -> f.annotation().order())
                    .thenComparing(f -> f.definition().key));
            return fields;
        }
    };
    private static final ItemDataComponentKey<Snapshot, CompoundTag> DATA = ItemDataComponentKey.create(
            "gtceu:data_stick_settings", snapshot -> {
                var tag = new CompoundTag();
                tag.putString("machine", snapshot.machine());
                tag.putInt("version", snapshot.version());
                tag.putByteArray("fields", snapshot.fields());
                return tag;
            }, tag -> new Snapshot(tag.getString("machine"), tag.getInt("version"), tag.getByteArray("fields")));

    private DataStickData() {}

    public static void init() {
        FieldDefinitionStorage.registerAnnotation(SaveToDataStick.class, Metadata::create);
    }

    public static InteractionResult interact(MetaMachine machine, ItemStack stack, boolean copy, Player player) {
        if (FIELDS.get(machine.getClass()).length == 0) {
            return InteractionResult.PASS;
        }
        var manager = machine.getFieldDataManager();
        var id = machine.getDefinition().getId().toString();
        if (copy) {
            if (!machine.isRemote()) {
                IDataComponentItem.set(stack, DATA, new Snapshot(id, GTDataFixer.VERSION, writeFields(manager)));
                stack.setHoverName(Component.translatable("gtceu.machine.me.import_part.data_stick.name",
                        Component.translatable(machine.getDefinition().getDescriptionId())));
                player.sendSystemMessage(Component.translatable("gtceu.machine.me.import_copy_settings"));
            }
        } else {
            var snapshot = IDataComponentItem.get(stack, DATA);
            if (snapshot == null || !snapshot.machine().equals(id)) return InteractionResult.PASS;
            if (!machine.isRemote()) {
                readFields(manager, snapshot.fields(), snapshot.version());
                machine.onChanged();
                player.sendSystemMessage(Component.translatable("gtceu.machine.me.import_paste_settings"));
            }
        }
        return InteractionResult.sidedSuccess(machine.isRemote());
    }

    static byte[] writeFields(FieldDataManager manager) {
        var fields = FIELDS.get(manager.holder.getClass());
        var values = new HashMap<String, Object>(fields.length);
        for (var field : fields) {
            var definition = field.definition();
            var metadata = (Metadata) field.metadata();
            Object value;
            if (metadata.getter() != null) {
                value = metadata.codec().encode(OPS, metadata.get(manager.holder.getSource(definition)));
            } else {
                value = manager.writeFieldToValue(definition, OPS);
            }
            if (value == DataField.NOT_PERSISTED) {
                // Disk persistence omits defaults; a configuration copy must overwrite the target's value.
                if (!definition.hasDefaultValue()) continue;
                value = writeDefault(manager, definition, metadata);
                if (value == DataField.NOT_PERSISTED) continue;
            }
            values.put(definition.key, value);
        }
        // Store bytes immediately: mutable source values must never remain attached to the stick.
        return OPS.toBytes(values);
    }

    @SuppressWarnings("unchecked")
    private static <T> Object writeDefault(FieldDataManager manager, DataFieldDefinition<T> definition,
                                           Metadata metadata) {
        var source = manager.holder.getSource(definition);
        T value = definition.get(source);
        if (metadata.skip(source, value)) return DataField.NOT_PERSISTED;
        if (definition.field.getType().isPrimitive()) return value;
        return definition.instanceAsValue ? definition.encode(source, value, OPS) :
                ((DataSyncCodec<T>) DataSyncCodec.get(definition.field.getType())).encode(OPS, value);
    }

    static void readFields(FieldDataManager manager, byte[] bytes) {
        readFields(manager, bytes, GTDataFixer.VERSION);
    }

    private static void readFields(FieldDataManager manager, byte[] bytes, int version) {
        var ops = JavaValueOps.create(version);
        var values = ops.getStringMap(ops.fromBytes(bytes));
        for (var field : FIELDS.get(manager.holder.getClass())) {
            var definition = field.definition();
            var value = values.get(definition.key);
            if (value == null) continue;
            var metadata = (Metadata) field.metadata();
            if (metadata.setter() != null) {
                metadata.set(manager.holder.getSource(definition),
                        metadata.codec().decode(ops, value));
            } else {
                if (ops.isNull(value) && !definition.instanceAsValue) {
                    // DataSyncLib treats an empty container's null payload as absent on disk load.
                    // Pasting must actively clear an already populated destination instead.
                    var target = definition.get(manager.holder.getSource(definition));
                    if (target instanceof Collection<?> collection) collection.clear();
                    else if (target instanceof Map<?, ?> map) map.clear();
                }
                manager.readFieldFromValue(value, ops, definition);
            }
            manager.markFieldsForSync(definition);
        }
    }

    private record Metadata(MethodHandle skipSave, MethodHandle getter, MethodHandle setter,
                            ValueCodec<Object> codec) {

        @SuppressWarnings("unchecked")
        private static Metadata create(MethodHandles.Lookup lookup, DataFieldDefinition<?> definition,
                                       Class<?> type, SaveToDataStick annotation) {
            var owner = definition.field.getDeclaringClass();
            // Bind the library's typed predicate entry point instead of resolving @SaveToDisk again.
            var skip = ReflectUtil.createAdaptedMethodHandle(lookup,
                    ReflectUtil.getAccessibleMethod(DataFieldDefinition.class, "skipSave", Object.class,
                            type.isPrimitive() ? type : Object.class))
                    .bindTo(definition);
            MethodHandle getter = null;
            if (!annotation.getter().isEmpty()) {
                var method = ReflectUtil.getAccessibleMethod(owner, annotation.getter());
                type = method.getReturnType();
                getter = ReflectUtil.createAdaptedMethodHandle(lookup, method);
                if (annotation.setter().isEmpty()) {
                    throw new IllegalArgumentException("Projected data-stick fields require a setter: " + definition.key);
                }
            }
            var setter = annotation.setter().isEmpty() ? null :
                    ReflectUtil.createAdaptedMethodHandle(lookup,
                            ReflectUtil.getAccessibleMethod(owner, annotation.setter(), type));
            var codec = setter == null ? null :
                    ((DataSyncCodec<Object>) DataSyncCodec.get(ClassUtils.primitiveToWrapper(type))).toValueCodec().optional();
            return new Metadata(skip, getter, setter, codec);
        }

        private boolean skip(Object source, Object value) {
            try {
                return (boolean) skipSave.invoke(source, value);
            } catch (Throwable e) {
                throw new IllegalStateException("Cannot evaluate data-stick skip predicate", e);
            }
        }

        private Object get(Object source) {
            try {
                return getter.invoke(source);
            } catch (Throwable e) {
                throw new IllegalStateException("Cannot read data-stick configuration", e);
            }
        }

        private void set(Object source, Object value) {
            try {
                setter.invoke(source, value);
            } catch (Throwable e) {
                throw new IllegalStateException("Cannot apply data-stick configuration", e);
            }
        }
    }

    private record Snapshot(String machine, int version, byte[] fields) {}
}
