package com.gto.datasynclib.datasream.data;

public sealed interface CollectionData extends Data permits ListData, ArrayData, ByteArrayData, IntArrayData, LongArrayData {

    int size();

    boolean isEmpty();
}
