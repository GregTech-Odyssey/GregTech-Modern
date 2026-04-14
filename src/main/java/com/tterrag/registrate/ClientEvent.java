package com.tterrag.registrate;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.tterrag.registrate.util.nullness.NonNullFunction;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ClientEvent {

    public static void register() {
        var eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        eventBus.addListener(ClientEvent::onItemColorHandlersEvent);
        eventBus.addListener(ClientEvent::onBlockColorHandlersEvent);
        eventBus.addListener(ClientEvent::onFMLClientSetupEvent);
    }

    private static ConcurrentHashMap<Supplier<? extends Item>, Supplier<ItemColor>> ITEM_COLOR_HANDLERS = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<Supplier<? extends Block>, Supplier<BlockColor>> BLOCK_COLOR_HANDLERS = new ConcurrentHashMap<>();

    private static ConcurrentHashMap<Supplier<? extends Block>, Supplier<RenderType>> BLOCK_RENDER_TYPES = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<Supplier<? extends Fluid>, Supplier<RenderType>> FLUID_RENDER_TYPES = new ConcurrentHashMap<>();

    private static ConcurrentHashMap<Supplier<? extends BlockEntityType<?>>, BlockEntityRendererProvider<?>> BLOCK_ENTITY_RENDERERS = new ConcurrentHashMap();

    private static void onItemColorHandlersEvent(RegisterColorHandlersEvent.Item event) {
        ITEM_COLOR_HANDLERS.forEach((k, v) -> event.register(v.get(), k.get()));
        ITEM_COLOR_HANDLERS = null;
    }

    private static void onBlockColorHandlersEvent(RegisterColorHandlersEvent.Block event) {
        BLOCK_COLOR_HANDLERS.forEach((k, v) -> event.register(v.get(), k.get()));
        BLOCK_COLOR_HANDLERS = null;
    }

    private static void onFMLClientSetupEvent(FMLClientSetupEvent event) {
        BLOCK_RENDER_TYPES.forEach((k, v) -> ItemBlockRenderTypes.setRenderLayer(k.get(), v.get()));
        FLUID_RENDER_TYPES.forEach((k, v) -> ItemBlockRenderTypes.setRenderLayer(k.get(), v.get()));
        BLOCK_RENDER_TYPES = null;
        FLUID_RENDER_TYPES = null;
        BLOCK_ENTITY_RENDERERS.forEach((k, v) -> BlockEntityRenderers.register((BlockEntityType) k.get(), v));
        BLOCK_ENTITY_RENDERERS = null;
    }

    public static void registerItemColorHandlers(Supplier<? extends Item> item, Supplier<ItemColor> handler) {
        ITEM_COLOR_HANDLERS.put(item, handler);
    }

    public static void registerBlockColorHandlers(Supplier<? extends Block> block, Supplier<BlockColor> handler) {
        BLOCK_COLOR_HANDLERS.put(block, handler);
    }

    public static void setBlockRenderLayer(Supplier<? extends Block> block, Supplier<RenderType> type) {
        BLOCK_RENDER_TYPES.put(block, type);
    }

    public static void setFluidRenderLayer(Supplier<? extends Fluid> fluid, Supplier<RenderType> type) {
        FLUID_RENDER_TYPES.put(fluid, type);
    }

    public static <T extends BlockEntity> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<?>> blockEntity, Supplier<NonNullFunction<BlockEntityRendererProvider.Context, BlockEntityRenderer<? super T>>> renderer) {
        Function<BlockEntityRendererProvider.Context, BlockEntityRenderer<BlockEntity>> function = (Function) renderer.get();
        BLOCK_ENTITY_RENDERERS.put(blockEntity, function::apply);
    }
}
