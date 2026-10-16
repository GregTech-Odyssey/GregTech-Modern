package com.gregtechceu.gtceu.common.pipelike.cable;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconType;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.pipenet.IMaterialPipeType;
import com.gregtechceu.gtceu.client.model.PipeModel;

import net.minecraft.resources.ResourceLocation;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.*;

public enum Insulation implements IMaterialPipeType<WireProperties> {

    WIRE_SINGLE("single_wire", 0.1875F, wireGtSingle, 1, 2, 1, -1, null),
    WIRE_DOUBLE("double_wire", 0.3125F, wireGtDouble, 2, 2, 1, -1, null),
    WIRE_QUADRUPLE("quadruple_wire", 0.4375F, wireGtQuadruple, 4, 3, 2, -1, null),
    WIRE_OCTAL("octal_wire", 0.5625F, wireGtOctal, 8, 3, 2, -1, null),
    WIRE_HEX("hex_wire", 0.8125F, wireGtHex, 16, 3, 2, -1, null),
    CABLE_SINGLE("single_cable", 0.25F, cableGtSingle, 1, 1, 0, 0, WIRE_SINGLE),
    CABLE_DOUBLE("double_cable", 0.375F, cableGtDouble, 2, 1, 0, 1, WIRE_DOUBLE),
    CABLE_QUADRUPLE("quadruple_cable", 0.5F, cableGtQuadruple, 4, 1, 0, 2, WIRE_QUADRUPLE),
    CABLE_OCTAL("octal_cable", 0.625F, cableGtOctal, 8, 1, 0, 3, WIRE_OCTAL),
    CABLE_HEX("hex_cable", 0.875F, cableGtHex, 16, 1, 0, 4, WIRE_HEX);

    public static final ResourceLocation TYPE_ID = GTCEu.id("insulation");

    public final String name;
    public final float thickness;
    public final int amperage;
    public final int lossMultiplier;
    public final int losslessMaterialLoss;
    @Getter
    public final TagPrefix tagPrefix;
    public final int insulationLevel;
    public final boolean isCable;
    @Nullable
    public final Insulation bare;

    Insulation(String name, float thickness, TagPrefix tagPrefix, int amperage, int lossMultiplier, int losslessMaterialLoss, int insulationLevel, @Nullable Insulation bare) {
        this.name = name;
        this.thickness = thickness;
        this.tagPrefix = tagPrefix;
        this.amperage = amperage;
        this.lossMultiplier = lossMultiplier;
        this.losslessMaterialLoss = losslessMaterialLoss;
        this.insulationLevel = insulationLevel;
        this.bare = bare;
        this.isCable = bare != null;
    }

    @Override
    public float getThickness() {
        return thickness;
    }

    @Override
    public WireProperties modifyProperties(WireProperties baseProperties) {
        int base = baseProperties.getLossPerBlock();
        int lossPerBlock = !baseProperties.isSuperconductor() && base == 0 ? losslessMaterialLoss : base * lossMultiplier;
        return new WireProperties(baseProperties.getVoltage(), baseProperties.getAmperage() * amperage, lossPerBlock, baseProperties.isSuperconductor());
    }

    public boolean isCable() {
        return isCable;
    }

    @Override
    public ResourceLocation type() {
        return TYPE_ID;
    }

    public PipeModel createPipeModel(Material material) {
        Supplier<ResourceLocation> wireSideTexturePath = () -> MaterialIconType.wire.getBlockTexturePath(material.getMaterialIconSet(), true).withSuffix("_side");
        Supplier<ResourceLocation> wireEndTexturePath = () -> MaterialIconType.wire.getBlockTexturePath(material.getMaterialIconSet(), true).withSuffix("_end");
        Supplier<@Nullable ResourceLocation> wireSideOverlayTexturePath = () -> MaterialIconType.wire.getBlockTexturePath(material.getMaterialIconSet(), "side_overlay", true);
        Supplier<@Nullable ResourceLocation> wireEndOverlayTexturePath = () -> MaterialIconType.wire.getBlockTexturePath(material.getMaterialIconSet(), "end_overlay", true);
        PipeModel model = new PipeModel(thickness, isCable ? () -> GTCEu.id("block/cable/insulation_5") : wireSideTexturePath, wireEndTexturePath, wireSideOverlayTexturePath, wireEndOverlayTexturePath);
        if (isCable) {
            model.setEndOverlayTexture(GTCEu.id("block/cable/insulation_%s".formatted(insulationLevel)));
        }
        return model;
    }
}
