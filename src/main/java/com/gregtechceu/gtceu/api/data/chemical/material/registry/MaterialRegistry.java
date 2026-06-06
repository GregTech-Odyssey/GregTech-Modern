package com.gregtechceu.gtceu.api.data.chemical.material.registry;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

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

    public ResourceLocation id(java.lang.String name) {
        return template.withPath(name);
    }
}
