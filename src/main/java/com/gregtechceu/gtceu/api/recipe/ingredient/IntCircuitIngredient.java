package com.gregtechceu.gtceu.api.recipe.ingredient;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntCircuitIngredient extends StrictNBTIngredient {

    public static final ResourceLocation TYPE = GTCEu.id("circuit");

    public static final Item PROGRAMMED_CIRCUIT = GTItems.PROGRAMMED_CIRCUIT.get();

    public static final String Configuration = "Configuration";

    private static final IntCircuitIngredient[] CIRCUIT_INPUTS = new IntCircuitIngredient[33];

    static {
        for (int i = 0; i < 33; i++) {
            CIRCUIT_INPUTS[i] = new IntCircuitIngredient(i);
        }
    }

    public static IntCircuitIngredient of(int configuration) {
        return CIRCUIT_INPUTS[configuration];
    }

    public final int configuration;

    private IntCircuitIngredient(int configuration) {
        super(IntCircuitBehaviour.stack(configuration));
        this.configuration = configuration;
    }

    public CompoundTag toNbt() {
        var tag = new CompoundTag();
        tag.putInt(Configuration, configuration);
        return tag;
    }

    @Override
    public @NotNull JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "gtceu:circuit");
        json.addProperty("configuration", configuration);
        return json;
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null) return false;
        var item = stack.getItem();
        if (item != PROGRAMMED_CIRCUIT) return false;
        var tag = stack.getTag();
        if (tag == null) return false;
        if (tag.tags.get(IntCircuitIngredient.Configuration) instanceof IntTag intTag) {
            return intTag.getAsInt() == configuration;
        }
        return false;
    }

    @Override
    @NotNull
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return SERIALIZER;
    }

    public static final IIngredientSerializer<IntCircuitIngredient> SERIALIZER = new IIngredientSerializer<>() {

        @Override
        public @NotNull IntCircuitIngredient parse(FriendlyByteBuf buffer) {
            return of(buffer.readVarInt());
        }

        @Override
        public @NotNull IntCircuitIngredient parse(JsonObject json) {
            return of(json.get("configuration").getAsInt());
        }

        @Override
        public void write(FriendlyByteBuf buffer, IntCircuitIngredient ingredient) {
            buffer.writeVarInt(ingredient.configuration);
        }
    };
}
