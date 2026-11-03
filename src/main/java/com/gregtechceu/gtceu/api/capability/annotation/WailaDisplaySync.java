package com.gregtechceu.gtceu.api.capability.annotation;

import com.gregtechceu.gtceu.api.capability.IWailaDisplayProvider;
import com.gregtechceu.gtceu.datasynclib.WailaDisplaySyncData;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field that should reach the client whenever a player looks at the block with Jade.
 *
 * <p>
 * An {@link IWailaDisplayProvider} normally has to copy everything it wants to show into the
 * {@code CompoundTag} the server fills in {@code appendWailaData}, and read it back out of that tag
 * in {@code appendWailaTooltip}. A field carrying this annotation is copied for you: the server
 * writes the field into the tag while Jade collects the block's display data, and the client writes
 * it back into its own copy of the block entity (or machine) before {@code appendWailaTooltip} runs.
 * That method can then simply read the field, the same way it would on the server.
 * </p>
 *
 * <p>
 * The field does not need {@code @SyncToClient}: nothing is sent while nobody looks at the block,
 * which is what makes this the cheap way to expose server-side state that the client has no other
 * reason to know. A field that is already {@code @SyncToClient} does not need this annotation.
 * </p>
 *
 * <p>
 * The field is managed by DataSyncLib exactly like a
 * {@link com.gto.datasynclib.blockentity.SaveToItem @SaveToItem} payload: it needs no
 * {@code @SaveToDisk} of its own, inherited fields and fields reached through
 * {@link com.gto.datasynclib.annotations.AdditionalHolder @AdditionalHolder} work, and the value is
 * written with the field's own codec — so the field's type must be one DataSyncLib supports (a
 * primitive, or a type with a registered codec / factory). A field that also carries
 * {@code @SaveToDisk} keeps that annotation's {@code skipWhen} predicate and default-value filter,
 * and its {@code listener} fires on the client after the synced value is applied. Final primitives
 * cannot be annotated, because DataSyncLib has no access path for them.
 * </p>
 *
 * <p>
 * See {@link WailaDisplaySyncData} for the payload that carries the values.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface WailaDisplaySync {}
