package com.gregtechceu.gtceu.data.pack;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.addon.AddonFinder;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.client.renderer.block.MaterialBlockRenderer;
import com.gregtechceu.gtceu.client.renderer.block.OreBlockRenderer;
import com.gregtechceu.gtceu.client.renderer.block.SurfaceRockRenderer;
import com.gregtechceu.gtceu.client.renderer.item.TagPrefixItemRenderer;
import com.gregtechceu.gtceu.client.renderer.item.ToolItemRenderer;
import com.gregtechceu.gtceu.common.data.GTModels;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.fml.ModLoader;

import com.fast.fastcollection.OpenCacheHashSet;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class GTDynamicResourcePack implements PackResources {

    protected static final ObjectSet<String> CLIENT_DOMAINS = new OpenCacheHashSet<>();
    protected static final GTDynamicPackContents CONTENTS = new GTDynamicPackContents();
    protected static boolean loaded;

    private final String name;

    static {
        CLIENT_DOMAINS.addAll(Sets.newHashSet(GTCEu.MOD_ID, "minecraft", "forge"));
    }

    public GTDynamicResourcePack(String name) {
        this(name, AddonFinder.getAddons().stream().map(IGTAddon::addonModId).collect(Collectors.toSet()));
    }

    public GTDynamicResourcePack(String name, Collection<String> domains) {
        this.name = name;
        CLIENT_DOMAINS.addAll(domains);
    }

    public static void load() {
        if (loaded) return;
        if (!ModLoader.isLoadingStateValid()) return;
        loaded = true;
        long startTime = System.currentTimeMillis();
        MaterialBlockRenderer.reinitModels();
        TagPrefixItemRenderer.reinitModels();
        OreBlockRenderer.reinitModels();
        ToolItemRenderer.reinitModels();
        SurfaceRockRenderer.reinitModels();
        GTModels.registerMaterialFluidModels();
        GTCEu.LOGGER.info("GregTech Model loading took {}ms", System.currentTimeMillis() - startTime);
    }

    public static void addBlockModel(ResourceLocation loc, Supplier<JsonElement> obj) {
        CONTENTS.addToData(getModelLocation(loc), () -> obj.get().toString().getBytes(StandardCharsets.UTF_8));
    }

    public static void addItemModel(ResourceLocation loc, Supplier<JsonElement> obj) {
        CONTENTS.addToData(getItemModelLocation(loc), () -> obj.get().toString().getBytes(StandardCharsets.UTF_8));
    }

    public static void addBlockState(ResourceLocation loc, Supplier<JsonElement> obj) {
        CONTENTS.addToData(getBlockStateLocation(loc), () -> obj.get().toString().getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        if (elements.length > 0 && elements[0].equals("pack.png")) {
            return () -> GTCEu.class.getResourceAsStream("/icon.png");
        }
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type == PackType.CLIENT_RESOURCES) {
            return CONTENTS.getResource(location);
        }
        return null;
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, ResourceOutput resourceOutput) {
        if (packType == PackType.CLIENT_RESOURCES) {
            CONTENTS.listResources(namespace, path, resourceOutput);
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? CLIENT_DOMAINS : Set.of();
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> metaReader) {
        if (metaReader == PackMetadataSection.TYPE) {
            return (T) new PackMetadataSection(Component.literal("GTCEu dynamic assets"),
                    SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES));
        }
        return null;
    }

    @Override
    public String packId() {
        return this.name;
    }

    @Override
    public void close() {
        // NOOP
    }

    public static ResourceLocation getBlockStateLocation(ResourceLocation blockId) {
        return new ResourceLocation(blockId.getNamespace(),
                String.join("", "blockstates/", blockId.getPath(), ".json"));
    }

    public static ResourceLocation getModelLocation(ResourceLocation blockId) {
        return new ResourceLocation(blockId.getNamespace(), String.join("", "models/", blockId.getPath(), ".json"));
    }

    public static ResourceLocation getItemModelLocation(ResourceLocation itemId) {
        return new ResourceLocation(itemId.getNamespace(), String.join("", "models/item/", itemId.getPath(), ".json"));
    }
}
