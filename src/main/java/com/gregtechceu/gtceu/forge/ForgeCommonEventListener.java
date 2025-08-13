package com.gregtechceu.gtceu.forge;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.capability.compat.EUToFEProvider;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.item.armor.ArmorComponentItem;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEnderRegistry;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.capability.MedicalConditionTracker;
import com.gregtechceu.gtceu.common.capability.WorldIDSaveData;
import com.gregtechceu.gtceu.common.commands.GTCommands;
import com.gregtechceu.gtceu.common.data.*;
import com.gregtechceu.gtceu.common.fluid.potion.BottleItemFluidHandler;
import com.gregtechceu.gtceu.common.fluid.potion.PotionItemFluidHandler;
import com.gregtechceu.gtceu.common.item.ToggleEnergyConsumerBehavior;
import com.gregtechceu.gtceu.common.item.armor.IJetpack;
import com.gregtechceu.gtceu.common.item.armor.QuarkTechSuite;
import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.network.packets.SPacketSendWorldID;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.gregtechceu.gtceu.integration.map.ClientCacheManager;
import com.gregtechceu.gtceu.integration.map.WaypointManager;
import com.gregtechceu.gtceu.integration.map.cache.server.ServerCache;
import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = GTCEu.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeCommonEventListener {

    @SubscribeEvent
    public static void registerItemStackCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        final ItemStack itemStack = event.getObject();
        if (itemStack.getItem() instanceof PotionItem) {
            event.addCapability(GTCEu.id("potion_item_handler"), new PotionItemFluidHandler(itemStack));
        } else if (itemStack.is(Items.GLASS_BOTTLE)) {
            event.addCapability(GTCEu.id("bottle_item_handler"), new BottleItemFluidHandler(itemStack));
        }
    }

    @SubscribeEvent
    public static void registerEntityCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player entity) {
            final MedicalConditionTracker tracker = new MedicalConditionTracker(entity);
            event.addCapability(GTCEu.id("medical_condition_tracker"), new ICapabilitySerializable<CompoundTag>() {

                @Override
                public CompoundTag serializeNBT() {
                    return tracker.serializeNBT();
                }

                @Override
                public void deserializeNBT(CompoundTag arg) {
                    tracker.deserializeNBT(arg);
                }

                @Override
                public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                                                  @Nullable Direction arg) {
                    return GTCapability.CAPABILITY_MEDICAL_CONDITION_TRACKER.orEmpty(capability,
                            LazyOptional.of(() -> tracker));
                }
            });
        }
    }

    @SubscribeEvent
    public static void registerBlockEntityCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
        event.addCapability(GTCEu.id("fe_capability"), new EUToFEProvider(event.getObject()));
    }

    @SubscribeEvent
    public static void onMobEffectEvent(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack item = player.getItemBySlot(EquipmentSlot.HEAD);
            if (item.is(GTItems.QUANTUM_HELMET.asItem()) && GTCapabilityHelper.getElectricItem(item) != null) {
                IElectricItem helmet = GTCapabilityHelper.getElectricItem(item);
                MobEffectInstance effect = event.getEffectInstance();
                int cost = QuarkTechSuite.potionRemovalCost.getOrDefault(effect.getEffect(), -1);
                if (cost != -1) {
                    cost = cost * (effect.getAmplifier() + 1);
                    if (helmet.canUse(cost)) {
                        helmet.discharge(cost, helmet.getTier(), true, false, false);
                        event.setResult(Event.Result.DENY);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        var blockState = event.getLevel().getBlockState(event.getPos());
        if (blockState.hasBlockEntity() && blockState.getBlock() instanceof MetaMachineBlock block &&
                block.getMachine(event.getLevel(), event.getPos()) instanceof IInteractedMachine machine) {
            if (machine.onLeftClick(event.getEntity(), event.getLevel(), event.getHand(), event.getPos(),
                    event.getFace())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        GTCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public static void registerReloadListeners(AddReloadListenerEvent event) {
        GTRegistries.updateFrozenRegistry(event.getRegistryAccess());
    }

    @SubscribeEvent
    public static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            TaskHandler.onTickUpdate(serverLevel);
        }
    }

    @SubscribeEvent
    public static void worldLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            WaypointManager.updateDimension(event.getLevel());
        } else if (event.getLevel() instanceof ServerLevel serverLevel) {
            ServerCache.instance.maybeInitWorld(serverLevel);
        }
    }

    @SubscribeEvent
    public static void worldUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            TaskHandler.onWorldUnLoad(serverLevel);
            MultiblockWorldSavedData.getOrCreate(serverLevel).releaseExecutorService();
            ServerCache.instance.invalidateWorld(serverLevel);
        } else if (event.getLevel().isClientSide()) {
            ClientCacheManager.saveCaches();
        }
    }

    @SubscribeEvent
    public static void serverStarting(ServerStartingEvent event) {
        ServerLevel mainLevel = event.getServer().overworld();
        WorldIDSaveData.init(mainLevel);
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        ServerCache.instance.clear();
        VirtualEnderRegistry.release();
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        var levels = event.getServer().getAllLevels();
        for (var level : levels) {
            if (!level.isClientSide()) {
                MultiblockWorldSavedData.getOrCreate(level).releaseExecutorService();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoinServer(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            GTNetwork.NETWORK.sendToPlayer(new SPacketSendWorldID(), serverPlayer);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEntityLivingFallEvent(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.fallDistance < 3.2f)
                return;

            ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);

            if (boots.is(CustomTags.STEP_BOOTS) && boots.getItem() instanceof ArmorComponentItem armor) {
                armor.getArmorLogic().damageArmor(player, boots, player.damageSources().fall(),
                        (int) (player.fallDistance - 1.2f), EquipmentSlot.FEET);
                player.fallDistance = 0;
                event.setCanceled(true);
            } else if (chest.getItem() instanceof ArmorComponentItem armor &&
                    armor.getArmorLogic() instanceof IJetpack jetpack &&
                    jetpack.canUseEnergy(chest, jetpack.getEnergyPerUse()) &&
                    player.fallDistance >= player.getHealth() + 3.2f) {
                        IJetpack.performEHover(chest, player);
                        player.fallDistance = 0;
                        event.setCanceled(true);
                    }
        }
    }

    @SubscribeEvent
    public static void stepAssistHandler(LivingEvent.LivingTickEvent event) {
        float MAGIC_STEP_HEIGHT = 1.0023f;
        if (event.getEntity() == null || !(event.getEntity() instanceof Player player)) return;
        if (!player.isCrouching() && player.getItemBySlot(EquipmentSlot.FEET).is(CustomTags.STEP_BOOTS)) {
            if (player.getStepHeight() < MAGIC_STEP_HEIGHT) {
                player.setMaxUpStep(MAGIC_STEP_HEIGHT);
            }
        } else if (player.getStepHeight() == MAGIC_STEP_HEIGHT) {
            player.setMaxUpStep(0.6f);
        }
    }

    @SubscribeEvent
    public static void onEntitySpawn(MobSpawnEvent.FinalizeSpawn event) {
        Mob entity = event.getEntity();
        Difficulty difficulty = entity.level().getDifficulty();
        if (difficulty == Difficulty.HARD && entity.getRandom().nextFloat() <= 0.03f) {
            if (entity instanceof Zombie zombie && ConfigHolder.INSTANCE.tools.nanoSaber.zombieSpawnWithSabers) {
                ItemStack itemStack = GTItems.NANO_SABER.get().getInfiniteChargedStack();
                ToggleEnergyConsumerBehavior.setItemActive(itemStack, true);
                entity.setItemSlot(EquipmentSlot.MAINHAND, itemStack);
                zombie.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            }
        }
    }
}
