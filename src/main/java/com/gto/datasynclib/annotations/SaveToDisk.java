package com.gto.datasynclib.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Extended from {@link AddToManager}
 * Marks a field to be SaveToDisk to disk.
 * Annotated fields will be automatically saved and loaded.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SaveToDisk {

    /**
     * Custom key name for storage.
     * If empty, the field name will be used as the key.
     * 
     * @return custom storage key name
     */
    String key() default "";
}
