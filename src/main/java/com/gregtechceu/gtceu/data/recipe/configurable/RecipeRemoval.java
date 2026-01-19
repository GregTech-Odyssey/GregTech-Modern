package com.gregtechceu.gtceu.data.recipe.configurable;

import com.gregtechceu.gtceu.api.data.chemical.material.MarkerMaterial;
import com.gregtechceu.gtceu.api.data.chemical.material.MarkerMaterials;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.recipe.misc.WoodMachineRecipes;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

import java.util.Locale;
import java.util.function.Consumer;

public class RecipeRemoval {

    public static void init(Consumer<ResourceLocation> registry) {
        generalRemovals(registry);
        WoodMachineRecipes.hardWoodRecipes(registry);
        if (ConfigHolder.INSTANCE.recipes.disableManualCompression) disableManualCompression(registry);
        if (ConfigHolder.INSTANCE.recipes.harderBrickRecipes) harderBrickRecipes(registry);
        if (ConfigHolder.INSTANCE.recipes.hardWoodRecipes) hardWoodRecipes(registry);
        if (ConfigHolder.INSTANCE.recipes.hardIronRecipes) hardIronRecipes(registry);
        if (ConfigHolder.INSTANCE.recipes.hardRedstoneRecipes) hardRedstoneRecipes(registry);
        if (ConfigHolder.INSTANCE.recipes.hardToolArmorRecipes) hardToolArmorRecipes(registry);
        if (ConfigHolder.INSTANCE.recipes.hardMiscRecipes) hardMiscRecipes(registry);
        if (ConfigHolder.INSTANCE.recipes.hardGlassRecipes) hardGlassRecipes(registry);
        if (ConfigHolder.INSTANCE.recipes.nerfPaperCrafting) nerfPaperCrafting(registry);
        if (ConfigHolder.INSTANCE.recipes.hardAdvancedIronRecipes) hardAdvancedIronRecipes(registry);
        if (ConfigHolder.INSTANCE.recipes.hardDyeRecipes) hardDyeRecipes(registry);
        if (ConfigHolder.INSTANCE.recipes.flintAndSteelRequireSteel) flintAndSteelRequireSteel(registry);
        if (ConfigHolder.INSTANCE.recipes.removeVanillaBlockRecipes) removeVanillaBlockRecipes(registry);
    }

    private static void generalRemovals(Consumer<ResourceLocation> registry) {
        if (ConfigHolder.INSTANCE.recipes.removeVanillaTNTRecipe)
            registry.accept(GTUtil.getResourceLocation("minecraft:tnt"));

        // todo
        /*
         * // always remove these, GT ore processing changes their output
         * ModHandler.removeFurnaceSmelting(new ItemStack(Blocks.COAL_ORE));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Blocks.IRON_ORE));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Blocks.GOLD_ORE));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Blocks.DIAMOND_ORE));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Blocks.EMERALD_ORE));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Blocks.LAPIS_ORE));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Blocks.REDSTONE_ORE));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Blocks.QUARTZ_ORE));
         *
         * // Remove a bunch of processing recipes for tools and armor, since we have significantly better options
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.IRON_HELMET, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.IRON_CHESTPLATE, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.IRON_LEGGINGS, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.IRON_BOOTS, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.IRON_HORSE_ARMOR, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.IRON_PICKAXE, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.IRON_SHOVEL, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.IRON_AXE, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.IRON_SWORD, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.IRON_HOE, 1, W));
         *
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.GOLDEN_HELMET, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.GOLDEN_CHESTPLATE, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.GOLDEN_LEGGINGS, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.GOLDEN_BOOTS, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.GOLDEN_HORSE_ARMOR, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.GOLDEN_PICKAXE, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.GOLDEN_SHOVEL, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.GOLDEN_AXE, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.GOLDEN_SWORD, 1, W));
         * ModHandler.removeFurnaceSmelting(new ItemStack(Items.GOLDEN_HOE, 1, W));
         */

        // removed these for parity with the other torch recipes
        registry.accept(GTUtil.getResourceLocation("minecraft:soul_torch"));
        registry.accept(GTUtil.getResourceLocation("minecraft:soul_lantern"));
        registry.accept(GTUtil.getResourceLocation("minecraft:leather_horse_armor"));

        // remove vanilla dye recipes to gregify
        registry.accept(GTUtil.getResourceLocation("minecraft:white_dye"));
    }

    /**
     * Remove recipes for any item that is 4x4 or 9x9 crafting (nuggets <-> ingot, ingot <-> block, etc.)
     */
    private static void disableManualCompression(Consumer<ResourceLocation> registry) {
        registry.accept(GTUtil.getResourceLocation("minecraft:gold_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:gold_nugget"));
        registry.accept(GTUtil.getResourceLocation("minecraft:gold_ingot_from_gold_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:gold_ingot_from_nuggets"));
        registry.accept(GTUtil.getResourceLocation("minecraft:coal_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:coal"));
        registry.accept(GTUtil.getResourceLocation("minecraft:redstone_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:redstone"));
        registry.accept(GTUtil.getResourceLocation("minecraft:emerald_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:emerald"));
        registry.accept(GTUtil.getResourceLocation("minecraft:diamond_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:diamond"));
        registry.accept(GTUtil.getResourceLocation("minecraft:iron_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:iron_nugget"));
        registry.accept(GTUtil.getResourceLocation("minecraft:iron_ingot_from_iron_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:iron_ingot_from_nuggets"));
        registry.accept(GTUtil.getResourceLocation("minecraft:lapis_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:lapis_lazuli"));
        registry.accept(GTUtil.getResourceLocation("minecraft:quartz_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:clay"));
        registry.accept(GTUtil.getResourceLocation("minecraft:nether_brick"));
        registry.accept(GTUtil.getResourceLocation("minecraft:glowstone"));
        registry.accept(GTUtil.getResourceLocation("minecraft:amethyst_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:copper_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:copper_ingot"));
        registry.accept(GTUtil.getResourceLocation("minecraft:copper_ingot_from_waxed_copper_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:honeycomb_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:snow_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:netherite_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:netherite_ingot_from_netherite_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:dripstone_block"));
    }

    private static void harderBrickRecipes(Consumer<ResourceLocation> registry) {
        registry.accept(GTUtil.getResourceLocation("minecraft:brick"));
        registry.accept(GTUtil.getResourceLocation("minecraft:bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:nether_brick"));
        registry.accept(GTUtil.getResourceLocation("minecraft:nether_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:red_nether_bricks"));
    }

    private static void hardWoodRecipes(Consumer<ResourceLocation> registry) {
        registry.accept(GTUtil.getResourceLocation("minecraft:ladder"));
        registry.accept(GTUtil.getResourceLocation("minecraft:bowl"));
        registry.accept(GTUtil.getResourceLocation("minecraft:chest"));
        registry.accept(GTUtil.getResourceLocation("minecraft:barrel"));
    }

    private static void hardIronRecipes(Consumer<ResourceLocation> registry) {
        registry.accept(GTUtil.getResourceLocation("minecraft:cauldron"));
        registry.accept(GTUtil.getResourceLocation("minecraft:hopper"));
        registry.accept(GTUtil.getResourceLocation("minecraft:iron_bars"));
        registry.accept(GTUtil.getResourceLocation("minecraft:bucket"));
        registry.accept(GTUtil.getResourceLocation("minecraft:chain"));
    }

    private static void hardRedstoneRecipes(Consumer<ResourceLocation> registry) {
        registry.accept(GTUtil.getResourceLocation("minecraft:dispenser"));
        registry.accept(GTUtil.getResourceLocation("minecraft:sticky_piston"));
        registry.accept(GTUtil.getResourceLocation("minecraft:piston"));
        registry.accept(GTUtil.getResourceLocation("minecraft:lever"));
        registry.accept(GTUtil.getResourceLocation("minecraft:daylight_detector"));
        registry.accept(GTUtil.getResourceLocation("minecraft:redstone_lamp"));
        registry.accept(GTUtil.getResourceLocation("minecraft:tripwire_hook"));
        registry.accept(GTUtil.getResourceLocation("minecraft:dropper"));
        registry.accept(GTUtil.getResourceLocation("minecraft:observer"));
        registry.accept(GTUtil.getResourceLocation("minecraft:repeater"));
        registry.accept(GTUtil.getResourceLocation("minecraft:comparator"));
        registry.accept(GTUtil.getResourceLocation("minecraft:powered_rail"));
        registry.accept(GTUtil.getResourceLocation("minecraft:detector_rail"));
        registry.accept(GTUtil.getResourceLocation("minecraft:rail"));
        registry.accept(GTUtil.getResourceLocation("minecraft:activator_rail"));
        registry.accept(GTUtil.getResourceLocation("minecraft:redstone_torch"));
        registry.accept(GTUtil.getResourceLocation("minecraft:stone_pressure_plate"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_blackstone_pressure_plate"));
        registry.accept(GTUtil.getResourceLocation("minecraft:heavy_weighted_pressure_plate"));
        registry.accept(GTUtil.getResourceLocation("minecraft:light_weighted_pressure_plate"));
        registry.accept(GTUtil.getResourceLocation("minecraft:stone_button"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_blackstone_button"));
        registry.accept(GTUtil.getResourceLocation("minecraft:calibrated_sculk_sensor"));
    }

    private static void hardToolArmorRecipes(Consumer<ResourceLocation> registry) {
        registry.accept(GTUtil.getResourceLocation("minecraft:compass"));
        registry.accept(GTUtil.getResourceLocation("minecraft:fishing_rod"));
        registry.accept(GTUtil.getResourceLocation("minecraft:clock"));
        registry.accept(GTUtil.getResourceLocation("minecraft:shears"));
        registry.accept(GTUtil.getResourceLocation("minecraft:shield"));
        registry.accept(GTUtil.getResourceLocation("minecraft:crossbow"));
        registry.accept(GTUtil.getResourceLocation("minecraft:bow"));
        for (String type : new String[] { "iron", "golden", "diamond" }) {
            registry.accept(GTUtil.getResourceLocation("minecraft:" + type + "_shovel"));
            registry.accept(GTUtil.getResourceLocation("minecraft:" + type + "_pickaxe"));
            registry.accept(GTUtil.getResourceLocation("minecraft:" + type + "_axe"));
            registry.accept(GTUtil.getResourceLocation("minecraft:" + type + "_sword"));
            registry.accept(GTUtil.getResourceLocation("minecraft:" + type + "_hoe"));
            registry.accept(GTUtil.getResourceLocation("minecraft:" + type + "_helmet"));
            registry.accept(GTUtil.getResourceLocation("minecraft:" + type + "_chestplate"));
            registry.accept(GTUtil.getResourceLocation("minecraft:" + type + "_leggings"));
            registry.accept(GTUtil.getResourceLocation("minecraft:" + type + "_boots"));
        }
    }

    /**
     * Remove recipes for items that don't fit in any other config option.
     * Vanilla items go here only if they not fit the criteria for removeVanillaBlockRecipes,
     * disableManualCompression, or any of the other config options
     */
    private static void hardMiscRecipes(Consumer<ResourceLocation> registry) {
        registry.accept(GTUtil.getResourceLocation("minecraft:jack_o_lantern"));
        registry.accept(GTUtil.getResourceLocation("minecraft:beacon"));
        registry.accept(GTUtil.getResourceLocation("minecraft:respawn_anchor"));
        registry.accept(GTUtil.getResourceLocation("minecraft:chiseled_bookshelf"));
        registry.accept(GTUtil.getResourceLocation("minecraft:brewing_stand"));
        registry.accept(GTUtil.getResourceLocation("minecraft:enchanting_table"));
        registry.accept(GTUtil.getResourceLocation("minecraft:jukebox"));
        registry.accept(GTUtil.getResourceLocation("minecraft:note_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:furnace"));
        registry.accept(GTUtil.getResourceLocation("minecraft:crafting_table"));
        registry.accept(GTUtil.getResourceLocation("minecraft:flower_pot"));
        registry.accept(GTUtil.getResourceLocation("minecraft:armor_stand"));
        registry.accept(GTUtil.getResourceLocation("minecraft:trapped_chest"));
        registry.accept(GTUtil.getResourceLocation("minecraft:ender_chest"));
        registry.accept(GTUtil.getResourceLocation("minecraft:lantern"));
        registry.accept(GTUtil.getResourceLocation("minecraft:stonecutter"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cartography_table"));
        registry.accept(GTUtil.getResourceLocation("minecraft:fletching_table"));
        registry.accept(GTUtil.getResourceLocation("minecraft:smithing_table"));
        registry.accept(GTUtil.getResourceLocation("minecraft:grindstone"));
        registry.accept(GTUtil.getResourceLocation("minecraft:smoker"));
        registry.accept(GTUtil.getResourceLocation("minecraft:blast_furnace"));
        registry.accept(GTUtil.getResourceLocation("minecraft:loom"));
        registry.accept(GTUtil.getResourceLocation("minecraft:composter"));
        registry.accept(GTUtil.getResourceLocation("minecraft:bell"));
        registry.accept(GTUtil.getResourceLocation("minecraft:conduit"));
        registry.accept(GTUtil.getResourceLocation("minecraft:candle"));
        registry.accept(GTUtil.getResourceLocation("minecraft:scaffolding"));
        registry.accept(GTUtil.getResourceLocation("minecraft:beehive"));
        registry.accept(GTUtil.getResourceLocation("minecraft:lightning_rod"));
        registry.accept(GTUtil.getResourceLocation("minecraft:lectern"));
        registry.accept(GTUtil.getResourceLocation("minecraft:golden_apple"));
        registry.accept(GTUtil.getResourceLocation("minecraft:book"));
        registry.accept(GTUtil.getResourceLocation("minecraft:ender_eye"));
        registry.accept(GTUtil.getResourceLocation("minecraft:glistering_melon_slice"));
        registry.accept(GTUtil.getResourceLocation("minecraft:golden_carrot"));
        registry.accept(GTUtil.getResourceLocation("minecraft:magma_cream"));
        registry.accept(GTUtil.getResourceLocation("minecraft:lead"));
        registry.accept(GTUtil.getResourceLocation("minecraft:item_frame"));
        registry.accept(GTUtil.getResourceLocation("minecraft:painting"));
        registry.accept(GTUtil.getResourceLocation("minecraft:chest_minecart"));
        registry.accept(GTUtil.getResourceLocation("minecraft:furnace_minecart"));
        registry.accept(GTUtil.getResourceLocation("minecraft:tnt_minecart"));
        registry.accept(GTUtil.getResourceLocation("minecraft:hopper_minecart"));
        for (DyeColor color : DyeColor.values()) {
            registry.accept(GTUtil.getResourceLocation(color.getName() + "_bed"));
        }
        registry.accept(GTUtil.getResourceLocation("minecraft:fermented_spider_eye"));
        registry.accept(GTUtil.getResourceLocation("minecraft:fire_charge"));
        registry.accept(GTUtil.getResourceLocation("minecraft:music_disc_5"));
        registry.accept(GTUtil.getResourceLocation("minecraft:turtle_helmet"));
        registry.accept(GTUtil.getResourceLocation("minecraft:brush"));
        registry.accept(GTUtil.getResourceLocation("minecraft:recovery_compass"));
        registry.accept(GTUtil.getResourceLocation("minecraft:spyglass"));
        registry.accept(GTUtil.getResourceLocation("minecraft:respawn_anchor"));
        registry.accept(GTUtil.getResourceLocation("minecraft:lodestone"));
        registry.accept(GTUtil.getResourceLocation("minecraft:chiseled_bookshelf"));
        registry.accept(GTUtil.getResourceLocation("minecraft:bread"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cake"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cookie"));
        registry.accept(GTUtil.getResourceLocation("minecraft:pumpkin_pie"));
    }

    private static void hardGlassRecipes(Consumer<ResourceLocation> registry) {
        registry.accept(GTUtil.getResourceLocation("minecraft:glass"));
        registry.accept(GTUtil.getResourceLocation("minecraft:glass_bottle"));
        registry.accept(GTUtil.getResourceLocation("minecraft:glass_pane"));
        for (DyeColor color : DyeColor.values()) {
            registry.accept(GTUtil.getResourceLocation(String.format("minecraft:%s_stained_glass_pane_from_glass_pane",
                    color.name().toLowerCase(Locale.ROOT))));
            registry.accept(GTUtil.getResourceLocation(
                    String.format("minecraft:%s_stained_glass_pane", color.name().toLowerCase(Locale.ROOT))));
        }
        registry.accept(GTUtil.getResourceLocation("minecraft:tinted_glass"));
    }

    private static void nerfPaperCrafting(Consumer<ResourceLocation> registry) {
        registry.accept(GTUtil.getResourceLocation("minecraft:paper"));
        registry.accept(GTUtil.getResourceLocation("minecraft:sugar_from_sugar_cane"));
    }

    private static void hardAdvancedIronRecipes(Consumer<ResourceLocation> registry) {
        registry.accept(GTUtil.getResourceLocation("minecraft:iron_door"));
        registry.accept(GTUtil.getResourceLocation("minecraft:anvil"));
        registry.accept(GTUtil.getResourceLocation("minecraft:iron_trapdoor"));
        registry.accept(GTUtil.getResourceLocation("minecraft:minecart"));
    }

    private static void hardDyeRecipes(Consumer<ResourceLocation> registry) {
        for (MarkerMaterial colorMaterial : MarkerMaterials.Color.VALUES) {
            registry.accept(
                    GTUtil.getResourceLocation(String.format("minecraft:%s_concrete_powder", colorMaterial.getName())));
            registry.accept(GTUtil.getResourceLocation(String.format("minecraft:%s_terracotta", colorMaterial.getName())));
            registry.accept(GTUtil.getResourceLocation(String.format("minecraft:%s_stained_glass", colorMaterial.getName())));
            registry.accept(GTUtil.getResourceLocation(String.format("minecraft:%s_candle", colorMaterial.getName())));
            registry.accept(GTUtil.getResourceLocation(String.format("minecraft:dye_%s_wool", colorMaterial.getName())));
            registry.accept(GTUtil.getResourceLocation(String.format("minecraft:dye_%s_carpet", colorMaterial.getName())));
            registry.accept(GTUtil.getResourceLocation(String.format("minecraft:dye_%s_bed", colorMaterial.getName())));
            registry.accept(GTUtil.getResourceLocation("minecraft:black_dye"));
            registry.accept(GTUtil.getResourceLocation("black_dye_from_wither_rose"));
            registry.accept(GTUtil.getResourceLocation("blue_dye"));
            registry.accept(GTUtil.getResourceLocation("white_dye_from_lily_of_the_valley"));
            registry.accept(GTUtil.getResourceLocation("light_blue_dye_from_blue_orchid"));
            registry.accept(GTUtil.getResourceLocation("yellow_dye_from_dandelion"));
            registry.accept(GTUtil.getResourceLocation("light_gray_dye_from_white_tulip"));
            registry.accept(GTUtil.getResourceLocation("light_gray_dye_from_azure_bluet"));
            registry.accept(GTUtil.getResourceLocation("red_dye_from_poppy"));
            registry.accept(GTUtil.getResourceLocation("red_dye_from_tulip"));
            registry.accept(GTUtil.getResourceLocation("red_dye_from_rose_bush"));
            registry.accept(GTUtil.getResourceLocation("red_dye_from_beetroot"));
            registry.accept(GTUtil.getResourceLocation("orange_dye_from_orange_tulip"));
            registry.accept(GTUtil.getResourceLocation("orange_dye_from_torchflower"));
            registry.accept(GTUtil.getResourceLocation("yellow_dye_from_dandelion"));
            registry.accept(GTUtil.getResourceLocation("cyan_dye_from_pitcher_plant"));
            registry.accept(GTUtil.getResourceLocation("light_blue_dye_from_blue_orchid"));
            registry.accept(GTUtil.getResourceLocation("blue_dye_from_cornflower"));
            registry.accept(GTUtil.getResourceLocation("magenta_dye_from_allium"));
            registry.accept(GTUtil.getResourceLocation("magenta_dye_from_lilac"));
            registry.accept(GTUtil.getResourceLocation("lime_dye_from_lime"));
            registry.accept(GTUtil.getResourceLocation("pink_dye_from_pink_tulip"));
            registry.accept(GTUtil.getResourceLocation("pink_dye_from_pink_petals"));
            registry.accept(GTUtil.getResourceLocation("pink_dye_from_peony"));
            registry.accept(GTUtil.getResourceLocation("yellow_dye_from_sunflower"));
            registry.accept(GTUtil.getResourceLocation("light_gray_dye_from_oxeye_daisy"));
        }
        registry.accept(GTUtil.getResourceLocation("minecraft:dark_prismarine"));
    }

    private static void flintAndSteelRequireSteel(Consumer<ResourceLocation> registry) {
        registry.accept(GTUtil.getResourceLocation("minecraft:flint_and_steel"));
    }

    /**
     * Removes the vanilla recipe for an item that would have BOTH a normal recipe as well as a GT recipe in
     * normal recipe configs (think stairs, ladders, etc. having a crafting table recipe as well as a machine recipe)
     */
    private static void removeVanillaBlockRecipes(Consumer<ResourceLocation> registry) {
        registry.accept(GTUtil.getResourceLocation("minecraft:dripstone_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_granite"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_diorite"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_andesite"));
        registry.accept(GTUtil.getResourceLocation("minecraft:packed_ice"));
        registry.accept(GTUtil.getResourceLocation("minecraft:blue_ice"));
        registry.accept(GTUtil.getResourceLocation("minecraft:slime_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:slime_ball"));
        registry.accept(GTUtil.getResourceLocation("minecraft:melon"));
        registry.accept(GTUtil.getResourceLocation("minecraft:hay_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:wheat"));
        registry.accept(GTUtil.getResourceLocation("minecraft:magma_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:nether_wart_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:bone_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:bone_meal_from_bone_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:honey_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:purpur_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:prismarine_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:prismarine"));
        registry.accept(GTUtil.getResourceLocation("minecraft:snow_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:sandstone"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_andesite"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_diorite"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_granite"));
        registry.accept(GTUtil.getResourceLocation("minecraft:coarse_dirt"));
        registry.accept(GTUtil.getResourceLocation("minecraft:chiseled_sandstone"));
        registry.accept(GTUtil.getResourceLocation("minecraft:chiseled_quartz_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:stone_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:chiseled_stone_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:purpur_pillar"));
        registry.accept(GTUtil.getResourceLocation("minecraft:end_stone_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:red_nether_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:red_sandstone"));
        registry.accept(GTUtil.getResourceLocation("minecraft:chiseled_red_sandstone"));
        registry.accept(GTUtil.getResourceLocation("minecraft:bookshelf"));
        registry.accept(GTUtil.getResourceLocation("minecraft:quartz_pillar"));
        registry.accept(GTUtil.getResourceLocation("minecraft:sea_lantern"));
        registry.accept(GTUtil.getResourceLocation("minecraft:white_wool_from_string"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cracked_stone_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:mossy_cobblestone_from_moss_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:mossy_cobblestone_from_vine"));
        registry.accept(GTUtil.getResourceLocation("minecraft:deepslate_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cracked_nether_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:chiseled_nether_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_blackstone_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cracked_polished_blackstone_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:quartz_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_deepslate"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_basalt"));
        registry.accept(GTUtil.getResourceLocation("minecraft:chiseled_polished_blackstone"));
        registry.accept(GTUtil.getResourceLocation("minecraft:deepslate_tiles"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cracked_deepslate_tiles"));
        registry.accept(GTUtil.getResourceLocation("minecraft:chiseled_deepslate"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cracked_deepslate_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cut_red_sandstone"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_basalt"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_blackstone"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cut_copper"));
        registry.accept(GTUtil.getResourceLocation("minecraft:exposed_cut_copper"));
        registry.accept(GTUtil.getResourceLocation("minecraft:weathered_cut_copper"));
        registry.accept(GTUtil.getResourceLocation("minecraft:oxidized_cut_copper"));
        registry.accept(GTUtil.getResourceLocation("minecraft:waxed_cut_copper"));
        registry.accept(GTUtil.getResourceLocation("minecraft:waxed_exposed_cut_copper"));
        registry.accept(GTUtil.getResourceLocation("minecraft:waxed_weathered_cut_copper"));
        registry.accept(GTUtil.getResourceLocation("minecraft:waxed_oxidized_cut_copper"));
        registry.accept(GTUtil.getResourceLocation("minecraft:end_crystal"));
        registry.accept(GTUtil.getResourceLocation("minecraft:end_rod"));
        registry.accept(GTUtil.getResourceLocation("minecraft:mud_bricks"));
        registry.accept(GTUtil.getResourceLocation("minecraft:mossy_stone_bricks_from_vine"));
        registry.accept(GTUtil.getResourceLocation("minecraft:mossy_stone_bricks_from_moss_block"));
        registry.accept(GTUtil.getResourceLocation("minecraft:packed_mud"));

        // Carpet replacement
        for (DyeColor color : DyeColor.values()) {
            registry.accept(GTUtil.getResourceLocation(String.format("minecraft:%s_carpet",
                    color.name().toLowerCase(Locale.ROOT))));
        }

        // Slab replacement
        registry.accept(GTUtil.getResourceLocation("minecraft:stone_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:smooth_stone_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:andesite_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:granite_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:diorite_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_andesite_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_granite_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_diorite_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:sandstone_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:smooth_sandstone_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:red_sandstone_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:smooth_red_sandstone_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cobblestone_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:blackstone_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_blackstone_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_blackstone_brick_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:brick_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:stone_brick_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:mud_brick_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:nether_brick_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:red_nether_brick_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:quartz_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:smooth_quartz_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cut_copper_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:exposed_cut_copper_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:oxidized_cut_copper_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:weathered_cut_copper_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:waxed_cut_copper_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:waxed_exposed_cut_copper_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:waxed_oxidized_cut_copper_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:waxed_weathered_cut_copper_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:red_sandstone_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:purpur_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:end_stone_brick_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:prismarine_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:prismarine_brick_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:dark_prismarine_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:mossy_cobblestone_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:mossy_stone_brick_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cut_sandstone_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cut_red_sandstone_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:bamboo_mosaic_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cobbled_deepslate_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_deepslate_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:deepslate_brick_slab"));
        registry.accept(GTUtil.getResourceLocation("minecraft:deepslate_tile_slab"));
        // stair
        registry.accept(GTUtil.getResourceLocation("minecraft:stone_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cobblestone_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:mossy_cobblestone_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:stone_brick_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:mossy_stone_brick_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:granite_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_granite_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:diorite_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_diorite_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:andesite_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_andesite_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cobbled_deepslate_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_deepslate_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:deepslate_brick_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:deepslate_tile_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:brick_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:mud_brick_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:sandstone_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:smooth_sandstone_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:red_sandstone_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:smooth_red_sandstone_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:prismarine_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:prismarine_brick_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:dark_prismarine_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:nether_brick_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:red_nether_brick_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:blackstone_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_blackstone_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_blackstone_brick_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:end_stone_brick_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:purpur_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:quartz_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:smooth_quartz_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cut_copper_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:exposed_cut_copper_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:weathered_cut_copper_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:oxidized_cut_copper_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:waxed_cut_copper_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:waxed_exposed_cut_copper_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:waxed_weathered_cut_copper_stairs"));
        registry.accept(GTUtil.getResourceLocation("minecraft:waxed_oxidized_cut_copper_stairs"));
        // wall
        registry.accept(GTUtil.getResourceLocation("minecraft:cobblestone_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:mossy_cobblestone_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:stone_brick_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:mossy_stone_brick_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:granite_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:diorite_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:andesite_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:cobbled_deepslate_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_deepslate_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:deepslate_brick_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:deepslate_tile_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:brick_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:mud_brick_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:sandstone_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:red_sandstone_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:prismarine_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:nether_brick_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:red_nether_brick_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:blackstone_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_blackstone_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:polished_blackstone_brick_wall"));
        registry.accept(GTUtil.getResourceLocation("minecraft:end_stone_brick_wall"));
    }
}
