package com.gto.datasynclib;

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
        registerCustomFactory(Class::isPrimitive, FieldDefinitionStorage::getPrimitiveFactory, 10000);
        registerCustomFactory(CombinationCodec::contains, k -> ObjCodecField::new, 100);

        registerCustomGenericFactory(CombinationCodec::contains, (t, gt) -> {
            var codec = CombinationCodec.get(t, gt);
            if (codec == null) throw new IllegalArgumentException("No codec for " + t + " " + Arrays.toString(gt));
            return d -> new CustomObjCodecField<>(d, codec);
        }, 100);

        registerAccessInterfaceFactory(IFieldDataHolder.class, k -> DataFieldHolderAccess::new, 1000);
        registerAccessInterfaceFactory(ISerializable.class, k -> SerializableAccess::new, 1000);

        registerAccessInterfaceFactory(IntCollection.class, k -> IntCollectionAccess::new, 1000);
        registerAccessInterfaceFactory(LongCollection.class, k -> LongCollectionAccess::new, 1000);
        registerAccessInterfaceFactory(Collection.class, k -> CollectionAccess::new, 100);
        registerAccessInterfaceFactory(Map.class, k -> MapAccess::new, 100);

        registerAccessCustomFactory(Class::isArray, c -> {
            var codec = CombinationCodec.get(c.componentType());
            if (codec == null) throw new IllegalArgumentException("No codec for " + c);
            return d -> new ArrayAccess<>(d, codec);
        }, 2000);
    }
}
