package com.gregtechceu.gtceu.common.unification.material;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.material.IMaterialRegistryManager;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.registry.MaterialRegistry;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.registry.GTRegistration;

import net.minecraft.resources.ResourceLocation;

import com.fast.fastcollection.O2OOpenCacheHashMap;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public final class MaterialRegistryManager implements IMaterialRegistryManager {

    private static final MaterialRegistryManager INSTANCE = new MaterialRegistryManager();

    public static final MaterialRegistry GREGTECH_REGISTRY = createInternalRegistry();

    private final Object2ObjectMap<String, MaterialRegistry> registries = new O2OOpenCacheHashMap<>();
    private final Int2ObjectMap<MaterialRegistry> networkIds = new Int2ObjectOpenHashMap<>();

    private Collection<Material> registeredMaterials;

    private final Set<Material> nonRegisteredMaterials = new ReferenceOpenHashSet<>();

    private Phase registrationPhase = Phase.PRE;

    private MaterialRegistryManager() {}

    public static MaterialRegistryManager getInstance() {
        return INSTANCE;
    }

    public void addNonRegistered(Material material) {
        nonRegisteredMaterials.add(material);
    }

    public Collection<Material> getAll() {
        return ImmutableList.<Material>builder().addAll(registeredMaterials).addAll(nonRegisteredMaterials).build();
    }

    @NotNull
    @Override
    public MaterialRegistry createRegistry(@NotNull GTRegistrate registrate) {
        if (registrationPhase != Phase.PRE) {
            throw new IllegalStateException("Cannot create registries in phase " + registrationPhase);
        }
        var modid = registrate.getModid();
        Preconditions.checkArgument(!registries.containsKey(modid),
                "Material registry already exists for modid %s", modid);
        MaterialRegistry registry = new MaterialRegistry(registrate);
        registries.put(modid, registry);
        networkIds.put(registry.getNetworkId(), registry);
        return registry;
    }

    @NotNull
    @Override
    public MaterialRegistry getRegistry(@NotNull String modid) {
        MaterialRegistry registry = registries.get(modid);
        return registry != null ? registry : GREGTECH_REGISTRY;
    }

    @NotNull
    @Override
    public MaterialRegistry getRegistry(int networkId) {
        MaterialRegistry registry = networkIds.get(networkId);
        return registry != null ? registry : GREGTECH_REGISTRY;
    }

    @NotNull
    @Override
    public Collection<MaterialRegistry> getRegistries() {
        if (registrationPhase == Phase.PRE) {
            throw new IllegalStateException("Cannot get all material registries during phase " + registrationPhase);
        }
        return Collections.unmodifiableCollection(registries.values());
    }

    @NotNull
    @Override
    public Collection<Material> getRegisteredMaterials() {
        if (registeredMaterials == null || (registrationPhase != Phase.CLOSED && registrationPhase != Phase.FROZEN)) {
            throw new IllegalStateException("Cannot retrieve all materials before registration");
        }
        return registeredMaterials;
    }

    @Override
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
            return getRegistry(modid).get(materialName);
        }
        return GTMaterials.NULL;
    }

    @Override
    public ResourceLocation getKey(Material material) {
        return material.getResourceLocation();
    }

    @NotNull
    @Override
    public Phase getPhase() {
        return registrationPhase;
    }

    public void unfreezeRegistries() {
        registries.values().forEach(MaterialRegistry::unfreeze);
        registrationPhase = Phase.OPEN;
    }

    public void closeRegistries() {
        registries.values().forEach(MaterialRegistry::closeRegistry);
        ImmutableList.Builder<Material> collection = ImmutableList.builder();
        for (MaterialRegistry registry : registries.values()) {
            collection.addAll(registry.getAllMaterials());
        }
        registeredMaterials = collection.build();
        registrationPhase = Phase.CLOSED;
    }

    public void freezeRegistries() {
        registries.values().forEach(MaterialRegistry::freeze);
        registrationPhase = Phase.FROZEN;
    }

    @NotNull
    private static MaterialRegistry createInternalRegistry() {
        MaterialRegistry registry = new MaterialRegistry(GTRegistration.REGISTRATE);
        INSTANCE.registries.put(GTCEu.MOD_ID, registry);
        return registry;
    }

    @NotNull
    public Material getDefaultFallback() {
        return GREGTECH_REGISTRY.getFallbackMaterial();
    }
}
