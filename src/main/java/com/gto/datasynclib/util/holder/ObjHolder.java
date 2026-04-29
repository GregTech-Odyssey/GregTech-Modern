package com.gto.datasynclib.util.holder;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ObjHolder<T> {

    public T value;

    public ObjHolder(T value) {
        this.value = value;
    }
}
