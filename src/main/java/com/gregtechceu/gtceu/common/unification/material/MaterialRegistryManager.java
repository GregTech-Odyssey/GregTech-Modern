package com.gregtechceu.gtceu.common.unification.material;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.registry.MaterialRegistry;
import com.gregtechceu.gtceu.api.registry.GTRegistry;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.registry.GTRegistration;

import net.minecraft.resources.ResourceLocation;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

public final class MaterialRegistryManager extends GTRegistry.RL<Material> {

    private static final MaterialRegistryManager INSTANCE = new MaterialRegistryManager();

    public static final MaterialRegistry GREGTECH_REGISTRY = createInternalRegistry();

    private final HashMap<String, MaterialRegistry> registries = new HashMap<>();

    private final ReferenceOpenHashSet<Material> registeredMaterials = new ReferenceOpenHashSet<>();
    private final ReferenceOpenHashSet<Material> nonRegisteredMaterials = new ReferenceOpenHashSet<>();

    private Phase registrationPhase = Phase.PRE;

    private MaterialRegistryManager() {
        super(GTCEu.id("material"));
    }

    public static MaterialRegistryManager getInstance() {
        return INSTANCE;
    }

    public void addNonRegistered(Material material) {
        nonRegisteredMaterials.add(material);
    }

    public Collection<Material> getAll() {
        return values();
    }

    @NotNull
    public MaterialRegistry createRegistry(@NotNull GTRegistrate registrate) {
        if (registrationPhase != Phase.PRE) {
            throw new IllegalStateException("Cannot create registries in phase " + registrationPhase);
        }
        var modid = registrate.getModid();
        Preconditions.checkArgument(!registries.containsKey(modid),
                "Material registry already exists for modid %s", modid);
        MaterialRegistry registry = new MaterialRegistry(registrate);
        registries.put(modid, registry);
        return registry;
    }

    @NotNull
    public MaterialRegistry getRegistry(@NotNull String modid) {
        MaterialRegistry registry = registries.get(modid);
        return registry != null ? registry : GREGTECH_REGISTRY;
    }

    @NotNull
    public Collection<MaterialRegistry> getRegistries() {
        if (registrationPhase == Phase.PRE) {
            throw new IllegalStateException("Cannot get all material registries during phase " + registrationPhase);
        }
        return registries.values();
    }

    public Set<Material> getRegisteredMaterials() {
        if (registrationPhase != Phase.CLOSED && registrationPhase != Phase.FROZEN) {
            throw new IllegalStateException("Cannot retrieve all materials before registration");
        }
        return registeredMaterials;
    }

    public Material getMaterial(@NotNull String name) {
        if (!name.isEmpty()) {
            String modid;
            String materialName;
            int index = name.indexOf(':');
            if (index >= 0) {
                modid = name.substring(0, index);
                materialName = name.substring(index + 1);
            } else {
                modid = GTCEu.MOD_ID;
                materialName = name;
            }
            return get(new ResourceLocation(modid, materialName));
        }
        return GTMaterials.NULL;
    }

    @Override
    public ResourceLocation getKey(Material material) {
        return material.getResourceLocation();
    }

    public void unfreezeRegistries() {
        registrationPhase = Phase.OPEN;
    }

    public void closeRegistries() {
        registrationPhase = Phase.CLOSED;
        super.unfreeze();
        registries.values().forEach(r -> registeredMaterials.addAll(r.getMaterials()));
        registeredMaterials.forEach(m -> super.register(m.getResourceLocation(), m));
        nonRegisteredMaterials.forEach(m -> super.register(m.getResourceLocation(), m));
        super.freeze();
    }

    public void freezeRegistries() {
        registrationPhase = Phase.FROZEN;
    }

    @NotNull
    private static MaterialRegistry createInternalRegistry() {
        MaterialRegistry registry = new MaterialRegistry(GTRegistration.REGISTRATE);
        INSTANCE.registries.put(GTCEu.MOD_ID, registry);
        return registry;
    }

    public boolean canModifyMaterials() {
        return registrationPhase != Phase.FROZEN && registrationPhase != Phase.PRE;
    }
}
