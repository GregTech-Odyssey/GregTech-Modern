package com.gto.datasynclib.datasream.data;

public sealed interface ImmutableData extends Data permits NullData, BooleanData, NumericData, CharData, StringData {

    @Override
    default Data copy() {
        return this;
    }
}
