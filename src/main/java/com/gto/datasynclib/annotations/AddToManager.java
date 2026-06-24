package com.gto.datasynclib.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Add this field to {@link com.gto.datasynclib.FieldDataManager}
 * 
 * @see SaveToDisk
 * @see SyncToClient
 * @see SyncToServer
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AddToManager {}
