package com.gregtechceu.gtceu.api.data.chemical.material.properties;

import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

public class BlastProperty implements IMaterialProperty {

    /**
     * Blast Furnace Temperature of this Material.
     * If below 1000K, Primitive Blast Furnace recipes will be also added.
     * If above 1750K, a Hot Ingot and its Vacuum Freezer recipe will be also added.
     * <p>
     * If a Material with this Property has a Fluid, its temperature
     * will be set to this if it is the default Fluid temperature.
     */
    private int blastTemperature;
    /**
     * The {@link GasTier} of this Material, representing which Gas EBF recipes will be generated.
     * <p>
     * Default: null, meaning no Gas EBF recipes.
     */
    private GasTier gasTier = null;
    /**
     * The duration of the EBF recipe, overriding the stock behavior.
     * <p>
     * Default: -1, meaning the duration will be: material.getAverageMass() * blastTemperature / 50
     */
    private int durationOverride = -1;
    /**
     * The EU/t of the EBF recipe, overriding the stock behavior.
     * <p>
     * Default: -1, meaning the EU/t will be 120.
     */
    private int EUtOverride = -1;
    /**
     * The duration of the EBF recipe, overriding the stock behavior.
     * <p>
     * Default: -1, meaning the duration will be: material.getMass() * 3
     */
    private int vacuumDurationOverride = -1;
    /**
     * The EU/t of the Vacuum Freezer recipe (if needed), overriding the stock behavior.
     * <p>
     * Default: -1, meaning the EU/t will be 120 EU/t.
     */
    private int vacuumEUtOverride = -1;

    public BlastProperty(int blastTemperature) {
        this.blastTemperature = blastTemperature;
    }

    public BlastProperty(int blastTemperature, GasTier gasTier) {
        this.blastTemperature = blastTemperature;
        this.gasTier = gasTier;
    }

    public BlastProperty(int blastTemperature, GasTier gasTier, int eutOverride, int durationOverride, int vacuumEUtOverride, int vacuumDurationOverride) {
        this.blastTemperature = blastTemperature;
        this.gasTier = gasTier;
        this.EUtOverride = eutOverride;
        this.durationOverride = durationOverride;
        this.vacuumEUtOverride = vacuumEUtOverride;
        this.vacuumDurationOverride = vacuumDurationOverride;
    }

    /**
     * Default property constructor.
     */
    public BlastProperty() {
        this(0);
    }

    public void setBlastTemperature(int blastTemp) {
        if (blastTemp <= 0) throw new IllegalArgumentException("Blast Temperature must be greater than zero!");
        this.blastTemperature = blastTemp;
    }

    @Override
    public void verifyProperty(MaterialProperties properties) {
        properties.ensureSet(PropertyKey.INGOT, true);
    }

    public static GasTier validateGasTier(String gasTierName) {
        if (gasTierName == null) return null;
        else if ("LOW".equalsIgnoreCase(gasTierName)) return GasTier.LOW;
        else if ("MID".equalsIgnoreCase(gasTierName)) return GasTier.MID;
        else if ("HIGH".equalsIgnoreCase(gasTierName)) return GasTier.HIGH;
        else if ("HIGHER".equalsIgnoreCase(gasTierName)) return GasTier.HIGHER;
        else if ("HIGHEST".equalsIgnoreCase(gasTierName)) return GasTier.HIGHEST;
        else {
            String message = "Gas Tier must be either \"LOW\", \"MID\", \"HIGH\", \"HIGHER\", or \"HIGHEST\"";
            throw new IllegalArgumentException("Could not find valid gas tier for name: " + gasTierName + ". " + message);
        }
    }

    public enum GasTier {

        // Tiers used by GTCEu
        LOW(() -> FluidIngredient.of(GTMaterials.Nitrogen.getFluid(1000))),
        MID(() -> FluidIngredient.of(GTMaterials.Helium.getFluid(100))),
        HIGH(() -> FluidIngredient.of(GTMaterials.Argon.getFluid(50))),
        // Tiers reserved for addons
        HIGHER(() -> FluidIngredient.of(GTMaterials.Neon.getFluid(25))),
        HIGHEST(() -> FluidIngredient.of(GTMaterials.Krypton.getFluid(10)));

        public static final GasTier[] VALUES = values();
        private Supplier<FluidIngredient> fluid;

        GasTier(Supplier<FluidIngredient> fluid) {
            this.fluid = Suppliers.memoize(fluid);
        }

        public void setFluid(Supplier<FluidIngredient> fluid) {
            this.fluid = Suppliers.memoize(fluid);
        }

        public FluidIngredient getFluid() {
            return fluid.get().copy();
        }
    }

    public static class Builder {

        private int temp;
        private GasTier gasTier;
        private int eutOverride = -1;
        private int durationOverride = -1;
        private int vacuumEUtOverride = -1;
        private int vacuumDurationOverride = -1;

        public Builder() {}

        public Builder temp(int temperature) {
            this.temp = temperature;
            return this;
        }

        public Builder temp(int temperature, GasTier gasTier) {
            this.temp = temperature;
            this.gasTier = gasTier;
            return this;
        }

        public Builder blastStats(int eutOverride) {
            this.eutOverride = eutOverride;
            return this;
        }

        public Builder blastStats(int eutOverride, int durationOverride) {
            this.eutOverride = eutOverride;
            this.durationOverride = durationOverride;
            return this;
        }

        public Builder vacuumStats(int eutOverride) {
            this.vacuumEUtOverride = eutOverride;
            return this;
        }

        public Builder vacuumStats(int eutOverride, int durationOverride) {
            this.vacuumEUtOverride = eutOverride;
            this.vacuumDurationOverride = durationOverride;
            return this;
        }

        public BlastProperty build() {
            return new BlastProperty(temp, gasTier, eutOverride, durationOverride, vacuumEUtOverride, vacuumDurationOverride);
        }
    }

    /**
     * Blast Furnace Temperature of this Material.
     * If below 1000K, Primitive Blast Furnace recipes will be also added.
     * If above 1750K, a Hot Ingot and its Vacuum Freezer recipe will be also added.
     * <p>
     * If a Material with this Property has a Fluid, its temperature
     * will be set to this if it is the default Fluid temperature.
     */
    public int getBlastTemperature() {
        return this.blastTemperature;
    }

    /**
     * The {@link GasTier} of this Material, representing which Gas EBF recipes will be generated.
     * <p>
     * Default: null, meaning no Gas EBF recipes.
     */
    public void setGasTier(final GasTier gasTier) {
        this.gasTier = gasTier;
    }

    /**
     * The {@link GasTier} of this Material, representing which Gas EBF recipes will be generated.
     * <p>
     * Default: null, meaning no Gas EBF recipes.
     */
    public GasTier getGasTier() {
        return this.gasTier;
    }

    /**
     * The duration of the EBF recipe, overriding the stock behavior.
     * <p>
     * Default: -1, meaning the duration will be: material.getAverageMass() * blastTemperature / 50
     */
    public void setDurationOverride(final int durationOverride) {
        this.durationOverride = durationOverride;
    }

    /**
     * The duration of the EBF recipe, overriding the stock behavior.
     * <p>
     * Default: -1, meaning the duration will be: material.getAverageMass() * blastTemperature / 50
     */
    public int getDurationOverride() {
        return this.durationOverride;
    }

    /**
     * The EU/t of the EBF recipe, overriding the stock behavior.
     * <p>
     * Default: -1, meaning the EU/t will be 120.
     */
    public void setEUtOverride(final int EUtOverride) {
        this.EUtOverride = EUtOverride;
    }

    /**
     * The EU/t of the EBF recipe, overriding the stock behavior.
     * <p>
     * Default: -1, meaning the EU/t will be 120.
     */
    public int getEUtOverride() {
        return this.EUtOverride;
    }

    /**
     * The duration of the EBF recipe, overriding the stock behavior.
     * <p>
     * Default: -1, meaning the duration will be: material.getMass() * 3
     */
    public void setVacuumDurationOverride(final int vacuumDurationOverride) {
        this.vacuumDurationOverride = vacuumDurationOverride;
    }

    /**
     * The duration of the EBF recipe, overriding the stock behavior.
     * <p>
     * Default: -1, meaning the duration will be: material.getMass() * 3
     */
    public int getVacuumDurationOverride() {
        return this.vacuumDurationOverride;
    }

    /**
     * The EU/t of the Vacuum Freezer recipe (if needed), overriding the stock behavior.
     * <p>
     * Default: -1, meaning the EU/t will be 120 EU/t.
     */
    public void setVacuumEUtOverride(final int vacuumEUtOverride) {
        this.vacuumEUtOverride = vacuumEUtOverride;
    }

    /**
     * The EU/t of the Vacuum Freezer recipe (if needed), overriding the stock behavior.
     * <p>
     * Default: -1, meaning the EU/t will be 120 EU/t.
     */
    public int getVacuumEUtOverride() {
        return this.vacuumEUtOverride;
    }
}
