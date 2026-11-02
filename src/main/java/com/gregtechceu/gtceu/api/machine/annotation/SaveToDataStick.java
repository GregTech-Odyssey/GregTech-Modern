package com.gregtechceu.gtceu.api.machine.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Includes a machine field in data-stick configuration copies. Sneak-use copies and normal use
 * pastes between machines with the same definition. Only the server changes data.
 *
 * <p>
 * The field is managed by DataSyncLib without requiring {@code @SaveToDisk}; add that annotation
 * separately when the setting must survive a world reload. Inherited fields and fields flattened
 * through {@code @AdditionalHolder} are supported, using their DataSyncLib storage keys and codecs.
 * Disk-load listeners also run after pasting. Custom data-stick interactions take priority.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SaveToDataStick {

    /** Optional zero-argument getter exporting a configuration view instead of the field itself. */
    String getter() default "";

    /** Optional void setter taking the exported value; required when a getter projects another type. */
    String setter() default "";

    /** Lower values are pasted first, for settings whose setters clear dependent configuration. */
    int order() default 0;
}
