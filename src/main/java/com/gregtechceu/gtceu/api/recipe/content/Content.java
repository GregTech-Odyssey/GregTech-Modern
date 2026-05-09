package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.GTValues;
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
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;

public final class Content<T extends ContentInner> {

    public static final int MAX_CHANCE = 10_000;

    public final T inner;
    public final int chance;
    public final int tierChanceBoost;
    public long amount;

    public Content(T inner) {
        this.inner = inner;
        this.amount = inner.amount;
        this.chance = MAX_CHANCE;
        this.tierChanceBoost = 0;
    }

    public Content(T inner, long amount) {
        this.inner = inner;
        this.amount = amount;
        this.chance = MAX_CHANCE;
        this.tierChanceBoost = 0;
    }

    public Content(T inner, int chance, int tierChanceBoost) {
        this.inner = inner;
        this.amount = inner.amount;
        this.chance = chance;
        this.tierChanceBoost = tierChanceBoost;
    }

    public Content(T inner, long amount, int chance, int tierChanceBoost) {
        this.inner = inner;
        this.amount = amount;
        this.chance = chance;
        this.tierChanceBoost = tierChanceBoost;
    }

    public Content(Content<T> content) {
        this.inner = content.inner;
        this.amount = content.amount;
        this.chance = content.chance;
        this.tierChanceBoost = content.tierChanceBoost;
    }

    public Content(Content<T> content, long amount) {
        this.inner = content.inner;
        this.amount = amount;
        this.chance = content.chance;
        this.tierChanceBoost = content.tierChanceBoost;
    }

    @Nullable
    public static <T extends ContentInner> Content<T> fromNbt(IContentSerializer<T> serializer, @Nullable Tag tag) {
        if (tag instanceof CompoundTag compoundTag && compoundTag.tags.get("content") instanceof CompoundTag content) {
            var ingredient = serializer.fromNbt(content);
            if (ingredient instanceof ContentInner inner && !inner.isEmpty()) return new Content<>(ingredient, compoundTag.tags.get("amount") instanceof LongTag longTag ? longTag.getAsLong() : ingredient.amount, getChance(compoundTag), getTierChanceBoost(compoundTag));
        }
        return null;
    }

    @Nullable
    public CompoundTag toNbt() {
        if (inner instanceof ContentInner contentInner && !contentInner.isEmpty()) {
            var t = new CompoundTag();
            addChance(t, this);
            t.put("content", contentInner.toNbt());
            t.putLong("amount", amount);
            return t;
        }
        return null;
    }

    public Content<T> copy() {
        return new Content<>(this);
    }

    public Content<T> copy(long multiplier) {
        if (multiplier == 1) {
            return new Content<>(this);
        } else {
            return new Content<>(this, amount * multiplier);
        }
    }

    public void shrink(long amount) {
        this.amount -= amount;
    }

    public boolean isChanced() {
        return chance > 0 && chance < MAX_CHANCE;
    }

    public long getChanceAmount(ChanceBoostFunction function, int recipeTier, int chanceTier) {
        if (this.chance == MAX_CHANCE) return this.amount;
        var chance = function.getBoostedChance(this, recipeTier, chanceTier);
        if (chance == MAX_CHANCE) return this.amount;
        if (chance == 0) return 0;
        return (long) ((double) GTValues.RNG.nextInt(chance) / MAX_CHANCE) * this.amount;
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
        if (chance == MAX_CHANCE) return;
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
        graphics.drawString(fontRenderer, s, (int) ((x + (width / 3.0F)) * 2 - fontRenderer.width(s) + 23), (int) ((y + (height / 3.0F) + 6) * 2 - height + (chance == MAX_CHANCE ? 0 : 10)), color);
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
