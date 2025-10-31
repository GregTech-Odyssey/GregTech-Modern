package com.gregtechceu.gtceu.api.data.chemical.material.properties;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.util.Mth;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class OreProperty implements IMaterialProperty {

    /**
     * List of Ore byproducts.
     * <p>
     * Default: none, meaning only this property's Material.
     * -- GETTER --
     * List of Ore byproducts.
     * <p>
     * Default: none, meaning only this property's Material.
     * 
     */
    @Getter
    private final List<Material> oreByProducts = new ObjectArrayList<>();
    /**
     * Crushed Ore output amount multiplier during Maceration.
     * <p>
     * Default: 1 (no multiplier).
     * -- SETTER --
     * Crushed Ore output amount multiplier during Maceration.
     * <p>
     * Default: 1 (no multiplier).
     * -- GETTER --
     * Crushed Ore output amount multiplier during Maceration.
     * <p>
     * Default: 1 (no multiplier).
     * 
     * 
     */
    @Getter
    @Setter
    private int oreMultiplier;
    /**
     * Byproducts output amount multiplier during Maceration.
     * <p>
     * Default: 1 (no multiplier).
     * -- SETTER --
     * Byproducts output amount multiplier during Maceration.
     * <p>
     * Default: 1 (no multiplier).
     * -- GETTER --
     * Byproducts output amount multiplier during Maceration.
     * <p>
     * Default: 1 (no multiplier).
     * 
     * 
     */
    @Getter
    @Setter
    private int byProductMultiplier;
    /**
     * Should ore block use the emissive texture.
     * <p>
     * Default: false.
     * -- SETTER --
     * Should ore block use the emissive texture.
     * <p>
     * Default: false.
     * -- GETTER --
     * Should ore block use the emissive texture.
     * <p>
     * Default: false.
     * 
     * 
     */
    @Getter
    @Setter
    private boolean emissive;
    /**
     * Material to which smelting of this Ore will result.
     * <p>
     * Material will have a Dust Property.
     * Default: none.
     */
    @NotNull
    private Material directSmeltResult = GTMaterials.NULL;
    /**
     * Material in which this Ore should be washed to give additional output.
     * <p>
     * Material will have a Fluid Property.
     * Default: none.
     */
    @NotNull
    private Material washedIn = GTMaterials.NULL;
    /**
     * The amount of Material that the ore should be washed in
     * in the Chemical Bath.
     * <p>
     * Default 100 mb
     */
    private int washedAmount = 100;
    /**
     * During Electromagnetic Separation, this Ore will be separated
     * into this Material and the Material specified by this field.
     * Limit 2 Materials
     * <p>
     * Material will have a Dust Property.
     * Default: none.
     * -- GETTER --
     * During Electromagnetic Separation, this Ore will be separated
     * into this Material and the Material specified by this field.
     * Limit 2 Materials
     * <p>
     * Material will have a Dust Property.
     * Default: none.
     * 
     */
    @Getter
    private final List<Material> separatedInto = new ObjectArrayList<>();

    public OreProperty(int oreMultiplier, int byProductMultiplier) {
        this.oreMultiplier = oreMultiplier;
        this.byProductMultiplier = byProductMultiplier;
        this.emissive = false;
    }

    public OreProperty(int oreMultiplier, int byProductMultiplier, boolean emissive) {
        this.oreMultiplier = oreMultiplier;
        this.byProductMultiplier = byProductMultiplier;
        this.emissive = emissive;
    }

    /**
     * Default values constructor.
     */
    public OreProperty() {
        this(1, 1);
    }

    public void setWashedIn(Material m, int washedAmount) {
        this.washedIn = m;
        this.washedAmount = washedAmount;
    }

    @NotNull
    public ObjectIntPair<Material> getWashedIn() {
        return ObjectIntPair.of(this.washedIn, this.washedAmount);
    }

    public void setSeparatedInto(Material... materials) {
        this.separatedInto.addAll(Arrays.asList(materials));
    }

    /**
     * Set the ore byproducts for this property
     *
     * @param materials the materials to use as byproducts
     */
    public void setOreByProducts(@NotNull Material @NotNull... materials) {
        setOreByProducts(Arrays.asList(materials));
    }

    /**
     * Set the ore byproducts for this property
     *
     * @param materials the materials to use as byproducts
     */
    public void setOreByProducts(@NotNull Collection<@NotNull Material> materials) {
        this.oreByProducts.clear();
        this.oreByProducts.addAll(materials);
    }

    /**
     * Add ore byproducts to this property
     *
     * @param materials the materials to add as byproducts
     */
    public void addOreByProducts(@NotNull Material @NotNull... materials) {
        this.oreByProducts.addAll(Arrays.asList(materials));
    }

    @NotNull
    public final Material getOreByProduct(int index) {
        if (this.oreByProducts.isEmpty()) return GTMaterials.NULL;
        return this.oreByProducts.get(Mth.clamp(index, 0, this.oreByProducts.size() - 1));
    }

    @NotNull
    public final Material getOreByProduct(int index, @NotNull Material fallback) {
        Material material = getOreByProduct(index);
        return !material.isNull() ? material : fallback;
    }

    @Override
    public void verifyProperty(MaterialProperties properties) {
        properties.ensureSet(PropertyKey.DUST, true);
        if (!directSmeltResult.isNull()) directSmeltResult.getProperties().ensureSet(PropertyKey.DUST, true);
        if (!washedIn.isNull()) washedIn.getProperties().ensureSet(PropertyKey.FLUID, true);
        separatedInto.forEach(m -> m.getProperties().ensureSet(PropertyKey.DUST, true));
        oreByProducts.forEach(m -> m.getProperties().ensureSet(PropertyKey.DUST, true));
    }

    /**
     * Material to which smelting of this Ore will result.
     * <p>
     * Material will have a Dust Property.
     * Default: none.
     */
    @NotNull
    public Material getDirectSmeltResult() {
        return this.directSmeltResult;
    }

    /**
     * Material to which smelting of this Ore will result.
     * <p>
     * Material will have a Dust Property.
     * Default: none.
     */
    public void setDirectSmeltResult(@NotNull final Material directSmeltResult) {
        if (directSmeltResult == null) {
            throw new NullPointerException("directSmeltResult is marked non-null but is null");
        }
        this.directSmeltResult = directSmeltResult;
    }

    /**
     * Material in which this Ore should be washed to give additional output.
     * <p>
     * Material will have a Fluid Property.
     * Default: none.
     */
    public void setWashedIn(@NotNull final Material washedIn) {
        if (washedIn == null) {
            throw new NullPointerException("washedIn is marked non-null but is null");
        }
        this.washedIn = washedIn;
    }
}
