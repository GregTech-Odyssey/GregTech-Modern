package com.gregtechceu.gtceu.api.data.chemical.material.registry;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.registry.GTRegistry;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.unification.material.MaterialRegistryManager;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.resources.ResourceLocation;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@Getter
public class MaterialRegistry extends GTRegistry.Str<Material> {

    private final GTRegistrate registrate;

    private final ResourceLocation template;

    @NotNull
    private Material fallbackMaterial = GTMaterials.NULL;

    public MaterialRegistry(GTRegistrate registrate) {
        super(GTUtil.getResourceLocation(registrate.getModid(), "material"));
        this.registrate = registrate;
        this.template = new ResourceLocation(registrate.getModid(), "");
    }

    public void register(Material material) {
        super.register(material.getName(), material);
    }

    @NotNull
    public Collection<Material> getAllMaterials() {
        return super.values();
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
