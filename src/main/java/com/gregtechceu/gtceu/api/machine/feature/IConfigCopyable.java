package com.gregtechceu.gtceu.api.machine.feature;

import net.minecraft.nbt.CompoundTag;

/**
 * Single protocol for machines whose settings can be copied by the machine memory card.
 * <p>
 * The copy card only ever does {@code machine instanceof IConfigCopyable} — it knows nothing about
 * voiding mode, input limits, circuits or any future setting. Adding a copyable setting therefore
 * means touching the machine that owns it, never the card.
 * <p>
 * Implementations stay one-liners by delegating to the building blocks in {@link ConfigCopySupport}.
 * Machines that inherit an implementation should chain through {@code super} so a subclass adds its
 * own settings on top of its parent's instead of replacing them.
 *
 * @see ConfigCopySupport
 */
public interface IConfigCopyable extends IMachineFeature {

    /**
     * Write this machine's copyable configuration into {@code tag}.
     * <p>
     * Only <b>append</b> keys this machine owns, and only when the machine actually exposes that
     * setting right now (e.g. an export bus has no circuit to write). The {@code writeXxx} helpers
     * in {@link ConfigCopySupport} already apply the matching {@code hasXxxConfig()} guard.
     */
    void writeConfigTo(CompoundTag tag);

    /**
     * Restore the configuration this machine understands from {@code tag}.
     * <p>
     * Keys that are absent, or that this machine does not support, must be ignored silently, so a
     * card copied from a different machine type pastes only the compatible subset and never throws.
     */
    void readConfigFrom(CompoundTag tag);
}
