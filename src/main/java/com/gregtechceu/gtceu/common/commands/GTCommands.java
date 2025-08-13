package com.gregtechceu.gtceu.common.commands;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OrePlacer;
import com.gregtechceu.gtceu.api.gui.factory.GTUIEditorFactory;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.registry.GTRegistry;
import com.gregtechceu.gtceu.common.commands.arguments.GTRegistryArgument;
import com.gregtechceu.gtceu.data.pack.GTDynamicDataPack;

import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;

import com.google.gson.JsonElement;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import java.nio.file.Path;
import java.util.*;

import static net.minecraft.commands.Commands.*;

public class GTCommands {

    private static final SimpleCommandExceptionType ERROR_GIVE_FAILED = new SimpleCommandExceptionType(
            Component.translatable("command.gtceu.cape.give.failed"));
    private static final SimpleCommandExceptionType ERROR_TAKE_FAILED = new SimpleCommandExceptionType(
            Component.translatable("command.gtceu.cape.take.failed"));
    private static final Dynamic2CommandExceptionType ERROR_USE_FAILED = new Dynamic2CommandExceptionType(
            (player, cape) -> Component.translatable("command.gtceu.cape.use.failed", player, cape));

    // spotless:off
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
                literal("gtceu")
                        .then(literal("ui_editor")
                                .requires(ctx -> ctx.hasPermission(LEVEL_ADMINS))
                                .executes(context -> {
                                    GTUIEditorFactory.INSTANCE.openUI(GTUIEditorFactory.INSTANCE, context.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(literal("place_vein")
                                .requires(ctx -> ctx.hasPermission(LEVEL_ADMINS))
                                .then(argument("vein", GTRegistryArgument.registry(GTRegistries.ORE_VEINS, ResourceLocation.class))
                                        .executes(context -> {
                                            return GTCommands.placeVein(context, BlockPos.containing(context.getSource().getPosition()));
                                        })
                                        .then(argument("position", BlockPosArgument.blockPos())
                                                .executes(context -> {
                                                    return GTCommands.placeVein(context, BlockPosArgument.getBlockPos(context, "position"));
                                                })))));
    }
    // spotless:on

    public static Collection<ServerPlayer> findPlayersFrom(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        // go through all variants of the used player target selectors to find the targeted players
        try {
            return Collections.singleton(ctx.getSource().getPlayerOrException());
        } catch (CommandSyntaxException ignored) {
            try {
                return EntityArgument.getPlayers(ctx, "targets");
            } catch (CommandSyntaxException e) {
                return EntityArgument.getPlayers(ctx, "target");
            }
        }
    }

    private static <T> int dumpDataRegistry(CommandContext<CommandSourceStack> context,
                                            GTRegistry<ResourceLocation, T> registry, Codec<T> codec, String folder) {
        Path parent = GTCEu.getGameDir().resolve("gtceu/dumped/data");
        var ops = RegistryOps.create(JsonOps.INSTANCE, context.getSource().registryAccess());
        int dumpedCount = 0;
        for (ResourceLocation id : registry.keys()) {
            T entry = registry.get(id);
            JsonElement json = codec.encodeStart(ops, entry).getOrThrow(false, GTCEu.LOGGER::error);
            GTDynamicDataPack.writeJson(id, folder, parent, json);
            dumpedCount++;
        }
        final int result = dumpedCount;
        context.getSource().sendSuccess(
                () -> Component.translatable("command.gtceu.dump_data.success", result,
                        registry.getRegistryName().toString(), parent.toString()),
                true);
        return result;
    }

    private static int placeVein(CommandContext<CommandSourceStack> context, BlockPos sourcePos) {
        GTOreDefinition vein = context.getArgument("vein", GTOreDefinition.class);
        ResourceLocation id = GTRegistries.ORE_VEINS.getKey(vein);

        ChunkPos chunkPos = new ChunkPos(sourcePos);
        ServerLevel level = context.getSource().getLevel();

        GeneratedVeinMetadata metadata = new GeneratedVeinMetadata(id, chunkPos, sourcePos, vein);
        RandomSource random = level.random;

        OrePlacer placer = new OrePlacer();
        OreGenerator generator = placer.getOreGenCache().getOreGenerator();

        try (BulkSectionAccess access = new BulkSectionAccess(level)) {
            var generated = generator.generateOres(new OreGenerator.VeinConfiguration(metadata, random), level,
                    chunkPos);
            if (generated.isEmpty()) {
                throw new CommandRuntimeException(Component.translatable("command.gtceu.place_vein.failure",
                        id.toString(), sourcePos.toString()));
            }
            for (long pos : generated.get().getGeneratedChunks()) {
                placer.placeVein(pos, random, access, generated.get(), AlwaysTrueTest.INSTANCE);
                level.getChunk(ChunkPos.getX(pos), ChunkPos.getZ(pos)).setUnsaved(true);
            }
            context.getSource().sendSuccess(() -> Component.translatable("command.gtceu.place_vein.success",
                    id.toString(), sourcePos.toString()), true);
        }

        return 1;
    }
}
