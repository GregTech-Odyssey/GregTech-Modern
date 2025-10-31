package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.item.ITagPrefixItem;
import com.gregtechceu.gtceu.client.renderer.item.TagPrefixItemRenderer;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class GTTurbineItem extends ComponentItem implements ITagPrefixItem {

    @Override
    public TagPrefix getTagPrefix() {
        return tagPrefix;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    private final Material material;

    @Override
    public Component getName(ItemStack stack) {
        return ITagPrefixItem.super.getName(stack);
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return ITagPrefixItem.super.getDescriptionId(stack);
    }

    @Override
    public Component getDescription() {
        return ITagPrefixItem.super.getDescription();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        ITagPrefixItem.super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
    }

    @Getter
    protected final int power;
    @Getter
    protected final int efficiency;
    @Getter
    protected final float doDamageToEntity;
    protected final TagPrefix tagPrefix;

    protected void attachComponents() {
        attachComponents(new TurbineRotorBehaviour());
    }

    public GTTurbineItem(Properties properties, TagPrefix tagPrefix, Material material) {
        super(properties);
        var property = material.getProperty(PropertyKey.ROTOR);
        this.material = material;
        this.power = property.getPower();
        this.efficiency = property.getEfficiency();
        this.doDamageToEntity = property.getDamage();

        this.tagPrefix = tagPrefix;
        attachComponents();
        if (GTCEu.isClientSide()) {
            TagPrefixItemRenderer.create(this, tagPrefix.materialIconType(), material.getMaterialIconSet());
        }
    }

    public static int getRotorMaxDamage(Material m) {
        if (m.isNull() || m.getProperty(PropertyKey.ROTOR) == null)
            return -1;
        return 800 * (int) Math.pow(m.getProperty(PropertyKey.ROTOR).getDurability(), 0.65);
    }
}
