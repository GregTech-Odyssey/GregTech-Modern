package com.gregtechceu.gtceu.common;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.addon.AddonFinder;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconType;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.WorldGenLayers;
import com.gregtechceu.gtceu.api.data.worldgen.generator.IndicatorGenerators;
import com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerators;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIFactory;
import com.gregtechceu.gtceu.api.gui.factory.GTUIEditorFactory;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIFactory;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidContainerIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.*;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.data.materials.GTFoods;
import com.gregtechceu.gtceu.common.item.tool.rotation.CustomBlockRotations;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;
import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.registry.GTRegistration;
import com.gregtechceu.gtceu.common.unification.material.MaterialRegistryManager;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.core.MixinHelpers;
import com.gregtechceu.gtceu.core.mixins.AbstractRegistrateAccessor;
import com.gregtechceu.gtceu.data.GregTechDatagen;
import com.gregtechceu.gtceu.data.lang.MaterialLangGenerator;
import com.gregtechceu.gtceu.data.loot.ChestGenHooks;
import com.gregtechceu.gtceu.data.loot.DungeonLootLoader;
import com.gregtechceu.gtceu.data.pack.GTDynamicDataPack;
import com.gregtechceu.gtceu.data.pack.GTDynamicResourcePack;
import com.gregtechceu.gtceu.data.pack.GTPackSource;
import com.gregtechceu.gtceu.data.recipe.GTCraftingComponents;
import com.gregtechceu.gtceu.forge.AlloyBlastPropertyAddition;
import com.gregtechceu.gtceu.forge.ForgeCommonEventListener;
import com.gregtechceu.gtceu.integration.map.WaypointManager;
import com.gregtechceu.gtceu.utils.input.KeyBind;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.registries.RegisterEvent;

import com.google.common.collect.Multimaps;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateLangProvider;
import com.tterrag.registrate.providers.RegistrateProvider;
import com.tterrag.registrate.util.nullness.NonNullConsumer;

import java.util.List;

public class CommonProxy {

    public CommonProxy() {
        // used for forge events (ClientProxy + CommonProxy)
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        eventBus.register(this);
        // must be set here because of KubeJS compat
        // trying to read this before the pre-init stage
        GTCEuAPI.materialManager = MaterialRegistryManager.getInstance();
        ConfigHolder.init();
        GTCEuAPI.initializeHighTier();
        if (GTCEu.isDev()) {
            ConfigHolder.INSTANCE.recipes.generateLowQualityGems = true;
            ConfigHolder.INSTANCE.compat.energy.enableFEConverters = true;
        }

        GTValueProviderTypes.init(eventBus);
        GTRegistries.init(eventBus);
        GTFeatures.init(eventBus);
        GTCommandArguments.init(eventBus);
        GTMobEffects.init(eventBus);
        GTParticleTypes.init(eventBus);
        ForgeCommonEventListener.init();
        init();
    }

    public static void init() {
        GTCEu.LOGGER.info("GTCEu common proxy init!");
        GTNetwork.init();
        UIFactory.register(MachineUIFactory.INSTANCE);
        UIFactory.register(CoverUIFactory.INSTANCE);
        UIFactory.register(GTUIEditorFactory.INSTANCE);
        GTPlacerTypes.init();
        GTRecipeCapabilities.init();
        GTToolTiers.init();
        GTElements.init();
        MaterialIconSet.init();
        MaterialIconType.init();
        initMaterials();
        TagPrefix.init();
        GTSoundEntries.init();
        GTDamageTypes.init();
        GTCovers.init();
        GTFluids.init();
        GTCreativeModeTabs.init();
        GTBlocks.init();
        GTEntityTypes.init();
        GTBlockEntities.init();
        GTRecipeTypes.init();
        GTRecipeCategories.init();
        GTMachineUtils.init();
        GTMachines.init();

        GTFoods.init();
        GTItems.init();
        GTDimensionMarkers.init();
        ChanceLogic.init();
        WaypointManager.init();

        // fabric exclusive, squeeze this in here to register before stuff is used
        GTRegistration.REGISTRATE.registerRegistrate();

        GregTechDatagen.init();
        // Register all material manager registries, for materials with mod ids.
        GTCEuAPI.materialManager.getRegistries().forEach(registry -> {
            // Force the material lang generator to be at index 0, so that addons' lang generators can override it.
            AbstractRegistrateAccessor accessor = (AbstractRegistrateAccessor) registry.getRegistrate();
            if (accessor.getDoDatagen().get()) {
                // noinspection UnstableApiUsage
                List<NonNullConsumer<? extends RegistrateProvider>> providers = Multimaps.asMap(accessor.getDatagens())
                        .get(ProviderType.LANG);
                if (providers.isEmpty()) {
                    providers.add(
                            (provider) -> MaterialLangGenerator.generate((RegistrateLangProvider) provider, registry));
                } else {
                    providers.addFirst(
                            (provider) -> MaterialLangGenerator.generate((RegistrateLangProvider) provider, registry));
                }
            }

            registry.getRegistrate()
                    .registerEventListeners(ModList.get().getModContainerById(registry.getModid())
                            .filter(FMLModContainer.class::isInstance)
                            .map(FMLModContainer.class::cast)
                            .map(FMLModContainer::getEventBus)
                            .orElse(FMLJavaModLoadingContext.get().getModEventBus()));
        });

        WorldGenLayers.registerAll();
        VeinGenerators.registerAddonGenerators();
        IndicatorGenerators.registerAddonGenerators();

        GTFeatures.init();
        GTFeatures.register();
        CustomBlockRotations.init();
        KeyBind.init();
        MachineOwner.init();

        FusionReactorMachine.registerFusionTier(GTValues.LuV, " (MKI)");
        FusionReactorMachine.registerFusionTier(GTValues.ZPM, " (MKII)");
        FusionReactorMachine.registerFusionTier(GTValues.UV, " (MKIII)");
    }

    private static void initMaterials() {
        MaterialRegistryManager managerInternal = (MaterialRegistryManager) GTCEuAPI.materialManager;
        GTCEu.LOGGER.info("Registering material registries");
        managerInternal.unfreezeRegistries();
        GTMaterials.init();
        MaterialRegistryManager.getInstance().getRegistry(GTCEu.MOD_ID).setFallbackMaterial(GTMaterials.Aluminium);
        AddonFinder.getAddons().forEach(IGTAddon::registerMaterials);
        managerInternal.closeRegistries();
        AlloyBlastPropertyAddition.addAlloyBlastProperties();
        managerInternal.freezeRegistries();
    }

    @SubscribeEvent
    public void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(BuiltInRegistries.LOOT_FUNCTION_TYPE.key()))
            ChestGenHooks.RandomWeightLootFunction.init();
    }

    @SubscribeEvent
    public void commonSetup(FMLCommonSetupEvent event) {
        GTCraftingComponents.init();
        GTRecipes.recipeAddition();
        event.enqueueWork(() -> {
            CraftingHelper.register(FluidContainerIngredient.TYPE, FluidContainerIngredient.SERIALIZER);
        });
    }

    @SubscribeEvent
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        GTCapability.register(event);
    }

    @SubscribeEvent
    public void registerPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(new GTPackSource("gtceu:dynamic_assets",
                    event.getPackType(),
                    Pack.Position.BOTTOM,
                    GTDynamicResourcePack::new));
        } else if (event.getPackType() == PackType.SERVER_DATA) {
            long startTime = System.currentTimeMillis();
            GTRecipes.recipeRemoval();
            // Initialize dungeon loot additions
            DungeonLootLoader.init();
            MixinHelpers.registryGTDynamicTags();
            GTCEu.LOGGER.info("GregTech Data loading took {}ms", System.currentTimeMillis() - startTime);

            event.addRepositorySource(new GTPackSource("gtceu:dynamic_data",
                    event.getPackType(),
                    Pack.Position.BOTTOM,
                    GTDynamicDataPack::new));
        }
    }
}
