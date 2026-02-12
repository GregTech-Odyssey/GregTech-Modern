package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.chance.boost.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GradientUtil;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.utils.ColorUtils;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Content {

    public static final int MAX_CHANCE = 10_000;

    public Object inner;
    public int chance;
    public int tierChanceBoost;

    public Content(Object inner, int chance, int tierChanceBoost) {
        this.inner = inner;
        this.chance = chance;
        this.tierChanceBoost = tierChanceBoost;
    }

    public static <T> Codec<Content> codec(RecipeCapability<T> capability) {
        return RecordCodecBuilder.create(instance -> instance.group(capability.serializer.codec().fieldOf("content").forGetter(capability::of), ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("chance", ChanceLogic.getMaxChancedValue()).forGetter(val -> val.chance), Codec.INT.optionalFieldOf("tierChanceBoost", 0).forGetter(val -> val.tierChanceBoost)).apply(instance, Content::new));
    }

    @Nullable
    public static Content fromNbt(RecipeCapability<?> capability, @Nullable Tag tag) {
        if (tag instanceof CompoundTag compoundTag && compoundTag.tags.get("content") instanceof CompoundTag content) {
            var ingredient = capability.serializer.fromNbt(content);
            if (ingredient instanceof ContentInner inner && !inner.isEmpty()) return new Content(ingredient, getChance(compoundTag), getTierChanceBoost(compoundTag));
        }
        return null;
    }

    @Nullable
    public CompoundTag toNbt() {
        if (inner instanceof ContentInner contentInner && !contentInner.isEmpty()) {
            var t = new CompoundTag();
            addChance(t, this);
            t.put("content", contentInner.toNbt());
            return t;
        }
        return null;
    }

    public <T> T getInner() {
        return (T) inner;
    }

    public Content copy(RecipeCapability<?> capability) {
        return new Content(capability.copyContent(inner), chance, tierChanceBoost);
    }

    public Content copy(RecipeCapability<?> capability, @NotNull ContentModifier modifier) {
        if (modifier == ContentModifier.IDENTITY || chance < MAX_CHANCE) {
            return copy(capability);
        } else {
            return new Content(capability.copyContent(inner, modifier), chance, tierChanceBoost);
        }
    }

    public boolean isChanced() {
        return chance > 0 && chance < MAX_CHANCE;
    }

    /**
     * Attempts to fix and round the given chance boost due to potential differences
     * between the max chance and {@link ChanceLogic#getMaxChancedValue()}.
     * <br />
     * The worst case would be {@code 5,001 / 10,000} , meaning the boost would
     * have to be halved to have the intended effect.
     *
     * @param chanceBoost the chance boost to be fixed
     * @return the fixed chance boost
     */
    private int fixBoost(int chanceBoost) {
        float error = (float) ChanceLogic.getMaxChancedValue() / MAX_CHANCE;
        int fixed = Math.round(Math.abs(chanceBoost) / error);
        return chanceBoost < 0 ? -fixed : fixed;
    }

    public IGuiTexture createOverlay(boolean perTick, int recipeTier, int chanceTier, @Nullable ChanceBoostFunction function) {
        return new IGuiTexture() {

            @Override
            @OnlyIn(Dist.CLIENT)
            public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
                drawChance(graphics, x, y, width, height, recipeTier, chanceTier, function);
                drawFluidAmount(graphics, x, y, width, height);
                if (perTick) {
                    drawTick(graphics, x, y, width, height);
                }
            }
        };
    }

    @OnlyIn(Dist.CLIENT)
    public void drawFluidAmount(GuiGraphics graphics, float x, float y, int width, int height) {
        if (inner instanceof FluidIngredient ingredient) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            graphics.pose().scale(0.5F, 0.5F, 1);
            long amount = ingredient.amount;
            Font fontRenderer = Minecraft.getInstance().font;
            String s = FormattingUtil.formatBuckets(amount);
            if (fontRenderer.width(s) > 32) s = FormattingUtil.formatNumberReadable(amount, true, FormattingUtil.DECIMAL_FORMAT_1F, "B");
            if (fontRenderer.width(s) > 32) s = FormattingUtil.formatNumberReadable(amount, true, FormattingUtil.DECIMAL_FORMAT_0F, "B");
            graphics.drawString(fontRenderer, s, (int) ((x + (width / 3.0F)) * 2 - fontRenderer.width(s) + 22), (int) ((y + (height / 3.0F) + 6) * 2), 16777215, true);
            graphics.pose().popPose();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void drawChance(GuiGraphics graphics, float x, float y, int width, int height, int recipeTier, int chanceTier, @Nullable ChanceBoostFunction function) {
        if (chance == ChanceLogic.getMaxChancedValue()) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.pose().scale(0.5F, 0.5F, 1);
        var func = function == null ? ChanceBoostFunction.OVERCLOCK : function;
        int chance = func.getBoostedChance(this, recipeTier, chanceTier);
        float chanceFloat = 1.0F * chance / MAX_CHANCE;
        String percent = FormattingUtil.formatNumber2Places(100 * chanceFloat);
        String s = chance == 0 ? LocalizationUtils.format("gtceu.gui.content.chance_nc_short") : percent + "%";
        int color = chance == 0 ? 16711680 : GradientUtil.toRGB(Mth.lerp(chanceFloat, 29.0F, 167.0F), 100.0F, 50.0F);
        Font fontRenderer = Minecraft.getInstance().font;
        graphics.drawString(fontRenderer, s, (int) ((x + (width / 3.0F)) * 2 - fontRenderer.width(s) + 23), (int) ((y + (height / 3.0F) + 6) * 2 - height), color(color), true);
        graphics.pose().popPose();
    }

    @OnlyIn(Dist.CLIENT)
    private int color(int color) {
        if (color != 0xFF0000) return color;
        double progress = Math.abs(System.currentTimeMillis() % 4000) / 4000.0d;
        float alpha = (float) ((Math.cos(progress * 2 * Math.PI) + 1) / 2.2 + 0.05);
        return ColorUtils.color(alpha, 1f, 0.0f, 0.0f);
    }

    @OnlyIn(Dist.CLIENT)
    public void drawTick(GuiGraphics graphics, float x, float y, int width, int height) {
        graphics.pose().pushPose();
        RenderSystem.disableDepthTest();
        graphics.pose().translate(0, 0, 400);
        graphics.pose().scale(0.5F, 0.5F, 1);
        String s = LocalizationUtils.format("gtceu.gui.content.tips.per_tick_short");
        int color = 16776960;
        Font fontRenderer = Minecraft.getInstance().font;
        graphics.drawString(fontRenderer, s, (int) ((x + (width / 3.0F)) * 2 - fontRenderer.width(s) + 23), (int) ((y + (height / 3.0F) + 6) * 2 - height + (chance == ChanceLogic.getMaxChancedValue() ? 0 : 10)), color);
        graphics.pose().popPose();
    }

    @Override
    public String toString() {
        return "Content{" + "ContentInner=" + inner + ", chance=" + chance + ", tierChanceBoost=" + tierChanceBoost + '}';
    }

    private static int getChance(CompoundTag tag) {
        if (tag.tags.get("chance") instanceof IntTag chance) {
            return chance.getAsInt();
        }
        return Content.MAX_CHANCE;
    }

    private static int getTierChanceBoost(CompoundTag tag) {
        if (tag.tags.get("tierChanceBoost") instanceof IntTag tierChanceBoost) {
            return tierChanceBoost.getAsInt();
        }
        return 0;
    }

    private static void addChance(CompoundTag tag, Content content) {
        if (content.chance != Content.MAX_CHANCE) {
            tag.putInt("chance", content.chance);
            if (content.tierChanceBoost != 0) {
                tag.putInt("tierChanceBoost", content.tierChanceBoost);
            }
        }
    }
}
