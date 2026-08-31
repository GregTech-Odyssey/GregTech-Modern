package com.gregtechceu.gtceu.common.pipelike.item;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.ItemPipeProperties;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.pipenet.IMaterialPipeType;
import com.gregtechceu.gtceu.client.model.PipeModel;

import net.minecraft.resources.ResourceLocation;

import lombok.Getter;

public enum ItemPipeType implements IMaterialPipeType<ItemPipeProperties> {

    SMALL("small", 0.375F, TagPrefix.pipeSmallItem, 0.5F, 1.5F),
    NORMAL("normal", 0.5F, TagPrefix.pipeNormalItem, 1.0F, 1.0F),
    LARGE("large", 0.625F, TagPrefix.pipeLargeItem, 2.0F, 0.75F),
    HUGE("huge", 0.75F, TagPrefix.pipeHugeItem, 4.0F, 0.5F),
    RESTRICTIVE_SMALL("small_restrictive", 0.375F, TagPrefix.pipeSmallRestrictive, 0.5F, 150.0F),
    RESTRICTIVE_NORMAL("normal_restrictive", 0.5F, TagPrefix.pipeNormalRestrictive, 1.0F, 100.0F),
    RESTRICTIVE_LARGE("large_restrictive", 0.625F, TagPrefix.pipeLargeRestrictive, 2.0F, 75.0F),
    RESTRICTIVE_HUGE("huge_restrictive", 0.75F, TagPrefix.pipeHugeRestrictive, 4.0F, 50.0F);

    public static final ResourceLocation TYPE_ID = GTCEu.id("item");
    public static final ItemPipeType[] VALUES = values();
    @Getter
    public final String name;
    @Getter
    private final float thickness;
    private final float rateMultiplier;
    private final float resistanceMultiplier;
    @Getter
    private final TagPrefix tagPrefix;

    ItemPipeType(String name, float thickness, TagPrefix orePrefix, float rateMultiplier, float resistanceMultiplier) {
        this.name = name;
        this.thickness = thickness;
        this.tagPrefix = orePrefix;
        this.rateMultiplier = rateMultiplier;
        this.resistanceMultiplier = resistanceMultiplier;
    }

    public boolean isRestrictive() {
        return ordinal() > 3;
    }

    @Override
    public ItemPipeProperties modifyProperties(ItemPipeProperties baseProperties) {
        return new ItemPipeProperties((int) ((baseProperties.getPriority() * resistanceMultiplier) + 0.5), baseProperties.getTransferRate() * rateMultiplier);
    }

    @Override
    public ResourceLocation type() {
        return TYPE_ID;
    }

    public PipeModel createPipeModel(Material material) {
        PipeModel model;
        ItemPipeType textureType = isRestrictive() ? VALUES[ordinal() - 4] : this;
        if (material.hasProperty(PropertyKey.WOOD)) {
            model = new PipeModel(thickness, () -> GTCEu.id("block/pipe/pipe_side_wood"), () -> GTCEu.id("block/pipe/pipe_%s_in_wood".formatted(textureType.name)), null, null);
        } else {
            model = new PipeModel(thickness, () -> GTCEu.id("block/pipe/pipe_side"), () -> GTCEu.id("block/pipe/pipe_%s_in".formatted(textureType.name)), null, null);
        }
        if (isRestrictive()) {
            model.setSideOverlayTexture(GTCEu.id("block/pipe/pipe_restrictive"));
        }
        return model;
    }
}
