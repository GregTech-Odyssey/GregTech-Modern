package com.gregtechceu.gtceu.api.data.chemical.material.registry;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.registry.GTRegistry;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The only store of materials: registration is open between {@link #open()} and {@link #close()}, modification until
 * {@link #lock()}, and everything is read-only afterwards. Ids are sorted resource locations, so iteration is stable.
 */
public final class MaterialRegistry extends GTRegistry.RL<Material> {

    private final Object2ObjectOpenHashMap<String, GTRegistrate> registrates = new Object2ObjectOpenHashMap<>();
    private Phase phase = Phase.PRE;

    public MaterialRegistry() {
        super(GTCEu.id("material"), Material::getResourceLocation);
    }

    public void open() {
        requirePhase(Phase.PRE, "open");
        unfreeze();
        phase = Phase.OPEN;
    }

    public void bindRegistrate(@NotNull String namespace, @NotNull GTRegistrate registrate) {
        if (phase != Phase.PRE && phase != Phase.OPEN) {
            throw new IllegalStateException("Cannot bind a registrate for %s in phase %s".formatted(namespace, phase));
        }
        if (registrates.putIfAbsent(namespace, registrate) != null) {
            throw new IllegalStateException("A registrate is already bound for material namespace " + namespace);
        }
    }

    @NotNull
    public GTRegistrate registrateOf(@NotNull Material material) {
        var registrate = registrates.get(material.getModid());
        if (registrate == null) throw new IllegalStateException("No registrate bound for material namespace " + material.getModid());
        return registrate;
    }

    public void register(@NotNull Material material) {
        register(material.getResourceLocation(), material);
    }

    @Override
    public <T extends Material> T register(ResourceLocation key, T value) {
        if (phase != Phase.OPEN) {
            throw new IllegalStateException("Material %s cannot be registered in phase %s".formatted(key, phase));
        }
        return super.register(key, value);
    }

    public void close() {
        requirePhase(Phase.OPEN, "close");
        freeze();
        phase = Phase.CLOSED;
    }

    public void lock() {
        requirePhase(Phase.CLOSED, "lock");
        phase = Phase.FROZEN;
    }

    public void requireModifiable(@NotNull Material material) {
        if (phase != Phase.OPEN && phase != Phase.CLOSED) {
            throw new IllegalStateException("Material %s cannot be modified in phase %s".formatted(material.getResourceLocation(), phase));
        }
    }

    @Override
    public @NotNull Set<Material> values() {
        requireReadable();
        return super.values();
    }

    @Override
    public @NotNull Iterator<Material> iterator() {
        requireReadable();
        return super.iterator();
    }

    @Override
    public void forEach(Consumer<? super Material> action) {
        requireReadable();
        super.forEach(action);
    }

    private void requireReadable() {
        if (phase == Phase.PRE || phase == Phase.OPEN) {
            throw new IllegalStateException("Cannot iterate materials in phase " + phase);
        }
    }

    @Nullable
    public Material parse(@NotNull String name) {
        if (name.isEmpty()) return null;
        var id = ResourceLocation.tryParse(name.indexOf(':') < 0 ? GTCEu.MOD_ID + ":" + name : name);
        return id == null ? null : get(id);
    }

    @NotNull
    public Material getOrThrow(@NotNull ResourceLocation id) {
        var material = get(id);
        if (material == null) throw new IllegalArgumentException("Unknown material " + id);
        return material;
    }

    @NotNull
    public Material getOrThrow(@NotNull String name) {
        var material = parse(name);
        if (material == null) throw new IllegalArgumentException("Unknown material " + name);
        return material;
    }

    public Phase getPhase() {
        return phase;
    }

    private void requirePhase(Phase expected, String action) {
        if (phase != expected) throw new IllegalStateException("Cannot " + action + " material registry in phase " + phase);
    }

    public enum Phase {
        PRE,
        OPEN,
        CLOSED,
        FROZEN
    }
}
