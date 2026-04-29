package com.gto.datasynclib.util;

import lombok.experimental.UtilityClass;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

@UtilityClass
public final class ReflectUtil {

    public Class<?>[] getFieldGenericTypeClasses(Type genericType) {
        if (!(genericType instanceof ParameterizedType pType)) throw new IllegalArgumentException("Field is not a parameterized type");
        Type[] actualTypeArguments = pType.getActualTypeArguments();
        Class<?>[] result = new Class<?>[actualTypeArguments.length];
        for (int i = 0; i < actualTypeArguments.length; i++) {
            result[i] = getRawType(actualTypeArguments[i]);
        }
        return result;
    }

    public Class<?> getRawType(Type type) {
        return switch (type) {
            case Class<?> aClass -> aClass;
            case GenericArrayType genericArrayType -> getRawType(genericArrayType.getGenericComponentType());
            case ParameterizedType parameterizedType -> getRawType(parameterizedType.getRawType());
            case null, default -> null;
        };
    }
}
