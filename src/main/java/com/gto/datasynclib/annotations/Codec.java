package com.gto.datasynclib.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Codec {

    // DataCodec的静态字段
    String saveCodec() default "";

    // ByteStreamCodec的静态字段
    String syncCodec() default "";

    // 对象方法，签名：T -> Data
    String writeToData() default "";

    // 对象方法，签名：Data -> T
    String readFromData() default "";

    // 对象方法，签名：FriendlyByteBuf, T -> void
    String writeToBuffer() default "";

    // 对象方法，签名：FriendlyByteBuf -> T
    String readFromBuffer() default "";
}
