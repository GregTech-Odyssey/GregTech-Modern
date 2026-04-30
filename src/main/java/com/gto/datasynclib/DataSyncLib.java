package com.gto.datasynclib;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;

import com.gto.datasynclib.field.*;
import com.gto.datasynclib.field.access.*;
import com.gto.datasynclib.field.object.CustomObjCodecField;
import com.gto.datasynclib.field.object.ObjCodecField;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.longs.LongCollection;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static com.gto.datasynclib.FieldDefinitionStorage.*;

public final class DataSyncLib {

    public static void init() {
        CombinationCodec.init();
        registerFactory(boolean.class, BooleanField::new);
        registerFactory(byte.class, ByteField::new);
        registerFactory(char.class, CharField::new);
        registerFactory(double.class, DoubleField::new);
        registerFactory(float.class, FloatField::new);
        registerFactory(int.class, IntField::new);
        registerFactory(long.class, LongField::new);
        registerFactory(short.class, ShortField::new);
        registerCustomFactory(CombinationCodec::contains, k -> ObjCodecField::new, 100);

        registerCustomGenericFactory(CombinationCodec::contains, (t, gt) -> {
            var codec = CombinationCodec.get(t, gt);
            if (codec == null) throw new IllegalArgumentException("No codec for " + t + " " + Arrays.toString(gt));
            return d -> new CustomObjCodecField<>(d, codec);
        }, 100);

        registerAccessFactory(boolean[].class, BooleanArrayAccess::new);
        registerAccessFactory(byte[].class, ByteArrayAccess::new);
        registerAccessFactory(int[].class, IntArrayAccess::new);
        registerAccessFactory(long[].class, LongArrayAccess::new);

        registerAccessInterfaceFactory(IFieldDataHolder.class, k -> DataFieldHolderAccess::new, 1000);
        registerAccessInterfaceFactory(ISerializable.class, k -> SerializableAccess::new, 1000);

        registerAccessInterfaceFactory(IntCollection.class, k -> IntCollectionAccess::new, 1000);
        registerAccessInterfaceFactory(LongCollection.class, k -> LongCollectionAccess::new, 1000);
        registerAccessInterfaceFactory(Collection.class, k -> CollectionAccess::new, 100);
        registerAccessInterfaceFactory(Map.class, k -> MapAccess::new, 100);

        registerAccessCustomFactory(c -> c.isArray() && !c.componentType().isPrimitive(), c -> {
            var codec = CombinationCodec.get(c.componentType());
            if (codec == null) throw new IllegalArgumentException("No codec for " + c);
            return d -> new ArrayAccess<>(d, codec);
        }, 2000);

        CombinationCodec.register(Material.class, Material.STREAM_CODEC, Material.DATA_CODEC);
    }
}
