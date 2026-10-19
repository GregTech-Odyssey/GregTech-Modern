package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterialBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Structure blocks of the MV/HV/EV bedrock vein drills (fluid drilling rigs and bedrock ore miners).
 */
public final class VeinDrillTier {

    private static final VeinDrillTier[] BY_TIER = new VeinDrillTier[GTValues.TIER_COUNT];

    public static final VeinDrillTier MV = new VeinDrillTier(GTValues.MV, GTBlocks.CASING_STEEL_SOLID::get, GTMaterials.Steel, "block/casings/solid/machine_casing_solid_steel");
    public static final VeinDrillTier HV = new VeinDrillTier(GTValues.HV, GTBlocks.CASING_TITANIUM_STABLE::get, GTMaterials.Titanium, "block/casings/solid/machine_casing_stable_titanium");
    public static final VeinDrillTier EV = new VeinDrillTier(GTValues.EV, GTBlocks.CASING_TUNGSTENSTEEL_ROBUST::get, GTMaterials.TungstenSteel, "block/casings/solid/machine_casing_robust_tungstensteel");

    private final Supplier<Block> casing;
    private final Material frame;
    private final ResourceLocation texture;

    private VeinDrillTier(int tier, Supplier<Block> casing, Material frame, String texture) {
        this.casing = casing;
        this.frame = frame;
        this.texture = GTCEu.id(texture);
        BY_TIER[tier] = this;
    }

    public static VeinDrillTier of(int tier) {
        var drillTier = tier >= 0 && tier < BY_TIER.length ? BY_TIER[tier] : null;
        return drillTier == null ? MV : drillTier;
    }

    public Block casing() {
        return casing.get();
    }

    public Block frame() {
        return GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, frame).get();
    }

    public ResourceLocation texture() {
        return texture;
    }
}
