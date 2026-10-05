package com.gregtechceu.gtceu.api.recipe.ingredient;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.templates.VoidFluidHandler;

import appeng.api.stacks.AEFluidKey;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

public class FluidContainerIngredient extends Ingredient {

    public static final ResourceLocation TYPE = GTCEu.id("fluid_container");
    @Getter
    @Nullable
    private final KeyIngredient fluid;
    @Getter
    private final long amount;

    public FluidContainerIngredient(@Nullable KeyIngredient fluid, long amount) {
        super(Stream.empty());
        this.fluid = fluid;
        this.amount = amount;
    }

    public FluidContainerIngredient(FluidStack fluidStack) {
        this(fluidStack.isEmpty() ? null : KeyIngredient.of(fluidStack), fluidStack.getAmount());
    }

    public FluidContainerIngredient(Fluid fluid, int amount) {
        this(KeyIngredient.fluid(fluid), amount);
    }

    private ItemStack[] cachedStacks;

    @Nonnull
    @Override
    public ItemStack[] getItems() {
        if (cachedStacks == null) {
            cachedStacks = fluid == null ? new ItemStack[0] : Arrays.stream(fluid.getFluids(Keys.saturatedInt(amount))).map(FluidUtil::getFilledBucket).filter(s -> !s.isEmpty()).toArray(ItemStack[]::new);
        }
        return this.cachedStacks;
    }

    @Override
    public @NotNull JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE.toString());
        json.add("fluid", fluidToJson());
        return json;
    }

    private JsonObject fluidToJson() {
        JsonObject json = new JsonObject();
        var ingredient = fluid;
        if (ingredient == null) {
            json.addProperty("empty", true);
            return json;
        }
        var display = ingredient.displayKey() instanceof AEFluidKey fluidKey ? fluidKey : null;
        if (ingredient.tagKey() != null) {
            json.addProperty("tag", ingredient.tagKey().location().toString());
        } else if (display != null) {
            json.addProperty("fluid", GTUtil.FLUID_ID.apply(display.getFluid()).toString());
        } else {
            throw new IllegalStateException("Unknown fluid ingredient type");
        }
        json.addProperty("amount", amount);
        if (ingredient.kind() != KeyIngredient.BASE && ingredient.kind() != KeyIngredient.TAG && display != null && display.getTag() != null) {
            json.addProperty("nbt", display.getTag().getAsString());
        }
        return json;
    }

    private static FluidContainerIngredient fluidFromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) throw new JsonSyntaxException("Fluid ingredient cannot be null");
        var json = element.getAsJsonObject();
        if (json.has("empty")) return new FluidContainerIngredient((KeyIngredient) null, 0);
        long amount = json.get("amount").getAsLong();
        var nbtJson = json.get("nbt");
        var nbt = nbtJson != null ? CraftingHelper.getNBT(nbtJson) : null;
        var fluid = json.get("fluid");
        if (fluid != null) {
            return new FluidContainerIngredient(KeyIngredient.of(GTUtil.FLUID_VALUE.apply(GTUtil.getResourceLocation(fluid.getAsString())), nbt), amount);
        }
        var tag = json.get("tag");
        if (tag != null) {
            return new FluidContainerIngredient(KeyIngredient.fluidTag(TagKey.create(Registries.FLUID, GTUtil.getResourceLocation(tag.getAsString())), nbt), amount);
        }
        throw new JsonSyntaxException("Unknown fluid ingredient type");
    }

    @Override
    public boolean isEmpty() {
        return this.fluid == null;
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || fluid == null) return false;
        return FluidUtil.getFluidContained(stack).map(fluid::test).orElse(false) && FluidUtil.tryEmptyContainer(stack, VoidFluidHandler.INSTANCE, Keys.saturatedInt(amount), null, false).isSuccess();
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    public ItemStack getExtractedStack(ItemStack input) {
        FluidActionResult result = FluidUtil.tryEmptyContainer(input, VoidFluidHandler.INSTANCE, Keys.saturatedInt(amount), ForgeHooks.getCraftingPlayer(), true);
        if (result.isSuccess()) {
            return result.getResult();
        }
        return input;
    }

    @Override
    @NotNull
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return SERIALIZER;
    }

    public static FluidContainerIngredient fromJson(JsonObject json) {
        return SERIALIZER.parse(json);
    }

    public static final IIngredientSerializer<FluidContainerIngredient> SERIALIZER = new IIngredientSerializer<>() {

        @Override
        @NotNull
        public FluidContainerIngredient parse(FriendlyByteBuf buffer) {
            if (!buffer.readBoolean()) return new FluidContainerIngredient((KeyIngredient) null, 0);
            KeyIngredient fluid = KeyIngredient.fromNetwork(buffer);
            return new FluidContainerIngredient(fluid, buffer.readVarLong());
        }

        @Override
        @NotNull
        public FluidContainerIngredient parse(JsonObject json) {
            return fluidFromJson(GsonHelper.getAsJsonObject(json, "fluid"));
        }

        @Override
        public void write(FriendlyByteBuf buffer, FluidContainerIngredient ingredient) {
            var fluid = ingredient.fluid;
            buffer.writeBoolean(fluid != null);
            if (fluid != null) {
                fluid.toNetwork(buffer);
                buffer.writeVarLong(ingredient.amount);
            }
        }
    };
}
