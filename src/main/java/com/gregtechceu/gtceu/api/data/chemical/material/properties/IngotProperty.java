package com.gregtechceu.gtceu.api.data.chemical.material.properties;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IngotProperty implements IMaterialProperty {

    /**
     * Specifies a material into which this material parts turn when heated
     */
    @Nullable
    private Material smeltingInto = null;
    /**
     * Specifies a material into which this material parts turn when heated in arc furnace
     */
    @Nullable
    private Material arcSmeltingInto = null;
    /**
     * Specifies a Material into which this Material Macerates into.
     * <p>
     * Default: this Material.
     */
    @Nullable
    private Material macerateInto = null;
    /**
     * Material which obtained when this material is polarized
     */
    @Nullable
    private Material magneticMaterial = null;

    @Override
    public void verifyProperty(MaterialProperties properties) {
        properties.ensureSet(PropertyKey.DUST, true);
        if (properties.hasProperty(PropertyKey.GEM)) {
            throw new IllegalStateException("Material " + properties.getMaterial() + " has both Ingot and Gem Property, which is not allowed!");
        }
        if (smeltingInto == null) smeltingInto = properties.getMaterial();
        else smeltingInto.getProperties().ensureSet(PropertyKey.INGOT, true);
        if (arcSmeltingInto == null) arcSmeltingInto = properties.getMaterial();
        else arcSmeltingInto.getProperties().ensureSet(PropertyKey.INGOT, true);
        if (macerateInto == null) macerateInto = properties.getMaterial();
        else macerateInto.getProperties().ensureSet(PropertyKey.INGOT, true);
        if (magneticMaterial != null) magneticMaterial.getProperties().ensureSet(PropertyKey.INGOT, true);
    }

    /**
     * Specifies a material into which this material parts turn when heated
     */
    @NotNull
    public Material getSmeltingInto() {
        return this.smeltingInto;
    }

    /**
     * Specifies a material into which this material parts turn when heated
     */
    public void setSmeltingInto(@NotNull final Material smeltingInto) {
        if (smeltingInto == null) {
            throw new NullPointerException("smeltingInto is marked non-null but is null");
        }
        this.smeltingInto = smeltingInto;
    }

    /**
     * Specifies a material into which this material parts turn when heated in arc furnace
     */
    @NotNull
    public Material getArcSmeltingInto() {
        return this.arcSmeltingInto;
    }

    /**
     * Specifies a material into which this material parts turn when heated in arc furnace
     */
    public void setArcSmeltingInto(@NotNull final Material arcSmeltingInto) {
        if (arcSmeltingInto == null) {
            throw new NullPointerException("arcSmeltingInto is marked non-null but is null");
        }
        this.arcSmeltingInto = arcSmeltingInto;
    }

    /**
     * Specifies a Material into which this Material Macerates into.
     * <p>
     * Default: this Material.
     */
    @NotNull
    public Material getMacerateInto() {
        return this.macerateInto;
    }

    /**
     * Specifies a Material into which this Material Macerates into.
     * <p>
     * Default: this Material.
     */
    public void setMacerateInto(@NotNull final Material macerateInto) {
        if (macerateInto == null) {
            throw new NullPointerException("macerateInto is marked non-null but is null");
        }
        this.macerateInto = macerateInto;
    }

    /**
     * Material which obtained when this material is polarized
     */
    @NotNull
    public Material getMagneticMaterial() {
        return this.magneticMaterial;
    }

    /**
     * Material which obtained when this material is polarized
     */
    public void setMagneticMaterial(@NotNull final Material magneticMaterial) {
        if (magneticMaterial == null) {
            throw new NullPointerException("magneticMaterial is marked non-null but is null");
        }
        this.magneticMaterial = magneticMaterial;
    }
}
