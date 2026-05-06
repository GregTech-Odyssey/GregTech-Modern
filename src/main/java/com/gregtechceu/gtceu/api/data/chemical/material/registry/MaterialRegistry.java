package com.gregtechceu.gtceu.api.data.chemical.material.registry;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.unification.material.MaterialRegistryManager;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@Getter
public class MaterialRegistry {

    private final GTRegistrate registrate;

    private final ResourceLocation template;

    private final ReferenceOpenHashSet<Material> materials = new ReferenceOpenHashSet<>();

    @NotNull
    private Material fallbackMaterial = GTMaterials.NULL;

    public MaterialRegistry(GTRegistrate registrate) {
        this.registrate = registrate;
        this.template = new ResourceLocation(registrate.getModid(), "");
    }

    public void register(Material material) {
        materials.add(material);
    }

    @NotNull
    public Collection<Material> getAllMaterials() {
        return materials;
    }

    public void setFallbackMaterial(@NotNull Material material) {
        this.fallbackMaterial = material;
    }

    @NotNull
    public Material getFallbackMaterial() {
        if (this.fallbackMaterial.isNull()) {
            this.fallbackMaterial = MaterialRegistryManager.getInstance().getDefaultFallback();
        }
        return this.fallbackMaterial;
    }

    public ResourceLocation id(java.lang.String name) {
        return template.withPath(name);
    }
}
