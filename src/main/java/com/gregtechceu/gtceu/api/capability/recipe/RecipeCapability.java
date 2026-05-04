package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.codec.DispatchedMapCodec;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.content.IContentSerializer;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.fast.fastcollection.O2IOpenCacheHashMap;
import com.gto.datasynclib.datasream.codec.ByteStreamCodec;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Used to detect whether a machine has a certain capability.
 */
public abstract class RecipeCapability<T> {

    public static final ByteStreamCodec<RecipeCapability<?>> STREAM_CODEC = GTRegistries.RECIPE_CAPABILITIES.streamCodec(ByteStreamCodec.STRING_CODEC);
    public static final Codec<RecipeCapability<?>> DIRECT_CODEC = GTRegistries.RECIPE_CAPABILITIES.codec();
    public static final Codec<Map<RecipeCapability<?>, List<Content>>> CODEC = new DispatchedMapCodec<>(
            RecipeCapability.DIRECT_CODEC,
            RecipeCapability::contentCodec);
    public static final Comparator<RecipeCapability<?>> COMPARATOR = Comparator.comparingInt(o -> o.sortIndex);

    public final String name;
    public final int color;
    public final boolean doRenderSlot;
    public final int sortIndex;
    public final IContentSerializer<T> serializer;
    public final Codec<List<Content>> contentSerializer;

    protected RecipeCapability(String name, int color, boolean doRenderSlot, int sortIndex,
                               IContentSerializer<T> serializer) {
        this.name = name;
        this.color = color;
        this.doRenderSlot = doRenderSlot;
        this.sortIndex = sortIndex;
        this.serializer = serializer;
        contentSerializer = Content.codec(this).listOf();
    }

    public static Codec<List<Content>> contentCodec(RecipeCapability<?> capability) {
        return capability.contentSerializer;
    }

    /**
     * deep copy of this content. recipe need it for searching and such things
     */
    public T copyInner(T content) {
        return content;
    }

    /**
     * deep copy and modify the size attribute for those Content that have the size attribute.
     */
    public T copyWithModifier(T content, ContentModifier modifier) {
        return copyInner(content);
    }

    @SuppressWarnings("unchecked")
    public final T copyContent(Object content) {
        return copyInner((T) content);
    }

    @SuppressWarnings("unchecked")
    public final T copyContent(Object content, ContentModifier modifier) {
        return copyWithModifier((T) content, modifier);
    }

    public T ofInner(Object o) {
        return (T) o;
    }

    public T of(Content content) {
        return (T) content.inner;
    }

    public String slotName(IO io) {
        return "%s_%s".formatted(name, io.name().toLowerCase(Locale.ROOT));
    }

    public String slotName(IO io, int index) {
        return "%s_%s_%s".formatted(name, io.name().toLowerCase(Locale.ROOT), index);
    }

    public MutableComponent getName() {
        return Component.translatable("recipe.capability.%s.name".formatted(name));
    }

    public MutableComponent getColoredName() {
        return getName().withStyle(style -> style.withColor(this.color));
    }

    public void addXEIInfo(WidgetGroup group, int xOffset, GTRecipeDefinition recipe, T content, boolean perTick,
                           boolean isInput, MutableInt yOffset) {}

    /**
     * Create a cache map for chanced outputs
     *
     * @return a map of this capability's content type -> integer
     */
    public Object2IntMap<T> makeChanceCache() {
        return new O2IOpenCacheHashMap<>();
    }
}
