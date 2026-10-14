package com.gregtechceu.gtceu.api.item.tool;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.item.IGTTool;
import com.gregtechceu.gtceu.api.sound.ExistingSoundEntry;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.gregtechceu.gtceu.common.item.tool.behavior.*;
import com.gregtechceu.gtceu.data.recipe.CustomTags;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.gto.fastcollection.fastutil.O2OOpenCacheHashMap;
import it.unimi.dsi.fastutil.chars.Char2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.*;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * 工具类型声明：一行一个常量，声明顺序即注册顺序；数值、标签、类别、语言、配方工具头都从这里读。
 */
public final class GTToolType {

    public static final int NOT_ELECTRIC = -1;

    private static final ObjectArrayList<GTToolType> ALL = new ObjectArrayList<>();
    private static final List<GTToolType> ALL_VIEW = Collections.unmodifiableList(ALL);
    private static final Map<String, GTToolType> BY_NAME = new O2OOpenCacheHashMap<>();
    private static final Char2ReferenceOpenHashMap<GTToolType> BY_SYMBOL = new Char2ReferenceOpenHashMap<>();

    public static final GTToolType SWORD = builder("sword").lang("%s Sword").className("gtceu.tool.class.sword", "Sword").actions(ToolActions.DEFAULT_SWORD_ACTIONS).toolTag(ItemTags.SWORDS).harvestTag(BlockTags.SWORD_EFFICIENT).toolStats(b -> b.attacking().attackDamage(3.0F).attackSpeed(-2.4F)).materialAmount(2 * GTValues.M).build();
    public static final GTToolType PICKAXE = builder("pickaxe").lang("%s Pickaxe").className("gtceu.tool.class.pickaxe", "Pickaxe").actions(ToolActions.DEFAULT_PICKAXE_ACTIONS).toolTag(ItemTags.PICKAXES).toolTag(ItemTags.CLUSTER_MAX_HARVESTABLES).harvestTag(BlockTags.MINEABLE_WITH_PICKAXE).toolStats(b -> b.blockBreaking().attackDamage(1.0F).attackSpeed(-2.8F)).materialAmount(3 * GTValues.M).build();
    public static final GTToolType SHOVEL = builder("shovel").lang("%s Shovel").className("gtceu.tool.class.shovel", "Shovel").actions(ToolActions.DEFAULT_SHOVEL_ACTIONS).toolTag(ItemTags.SHOVELS).harvestTag(BlockTags.MINEABLE_WITH_SHOVEL).toolStats(b -> b.blockBreaking().attackDamage(1.5F).attackSpeed(-3.0F).behaviors(BlockTransformBehavior.FLATTEN)).materialAmount(GTValues.M).build();
    public static final GTToolType AXE = builder("axe").lang("%s Axe").className("gtceu.tool.class.axe", "Axe").actions(ToolActions.DEFAULT_AXE_ACTIONS).toolTag(ItemTags.AXES).harvestTag(BlockTags.MINEABLE_WITH_AXE).toolStats(b -> b.blockBreaking().attackDamage(5.0F).attackSpeed(-3.2F).baseEfficiency(2.0F).behaviors(DisableShieldBehavior.INSTANCE, BlockTransformBehavior.STRIP, BlockTransformBehavior.SCRAPE, BlockTransformBehavior.WAX_OFF)).materialAmount(3 * GTValues.M).build();
    public static final GTToolType HOE = builder("hoe").lang("%s Hoe").className("gtceu.tool.class.hoe", "Hoe").actions(ToolActions.DEFAULT_HOE_ACTIONS).toolTag(ItemTags.HOES).harvestTag(BlockTags.MINEABLE_WITH_HOE).toolStats(b -> b.cannotAttack().attackSpeed(-1.0F).behaviors(BlockTransformBehavior.TILL)).materialAmount(2 * GTValues.M).build();
    public static final GTToolType MINING_HAMMER = builder("mining_hammer").lang("%s Mining Hammer").className("gtceu.tool.class.mining_hammer", "Mining Hammer").tooltip("item.gtceu.tool.mining_hammer.tooltip").toolTag(CustomTags.MINING_HAMMERS).harvestTag(BlockTags.MINEABLE_WITH_PICKAXE).toolStats(b -> b.blockBreaking().aoe(1, 1, 0).efficiencyMultiplier(0.4F).attackDamage(1.5F).attackSpeed(-3.2F).durabilityMultiplier(3.0F).behaviors(AoEBehavior.INSTANCE)).toolClasses(PICKAXE).materialAmount(6 * GTValues.M).build();
    public static final GTToolType SPADE = builder("spade").lang("%s Spade").className("gtceu.tool.class.spade", "Spade").tooltip("item.gtceu.tool.spade.tooltip").toolTag(CustomTags.SPADES).harvestTag(BlockTags.MINEABLE_WITH_SHOVEL).toolStats(b -> b.blockBreaking().aoe(1, 1, 0).efficiencyMultiplier(0.4F).attackDamage(1.5F).attackSpeed(-3.2F).durabilityMultiplier(3.0F).behaviors(AoEBehavior.INSTANCE, BlockTransformBehavior.FLATTEN)).toolClasses(SHOVEL).materialAmount(3 * GTValues.M).build();
    public static final GTToolType SCYTHE = builder("scythe").lang("%s Scythe").className("gtceu.tool.class.scythe", "Scythe").tooltip("item.gtceu.tool.scythe.tooltip").toolTag(CustomTags.SCYTHES).harvestTag(BlockTags.MINEABLE_WITH_HOE).toolStats(b -> b.blockBreaking().attacking().attackDamage(5.0F).attackSpeed(-3.0F).durabilityMultiplier(3.0F).aoe(2, 2, 2).behaviors(AoEBehavior.INSTANCE, BlockTransformBehavior.TILL, HarvestCropsBehavior.INSTANCE).canApplyEnchantment(EnchantmentCategory.DIGGER)).toolClasses(HOE).materialAmount(3 * GTValues.M).build();
    public static final GTToolType SAW = builder("saw").lang("%s Saw").className("gtceu.tool.class.saw", "Saw").symbol('s').toolTag(CustomTags.CRAFTING_SAWS).toolTag(CustomTags.SAWS).harvestTag(CustomTags.MINEABLE_WITH_SAW).toolStats(b -> b.crafting().damagePerCraftingAction(2).cannotAttack().attackSpeed(-2.6F).behaviors(HarvestIceBehavior.INSTANCE)).sound(GTSoundEntries.SAW_TOOL).materialAmount(2 * GTValues.M).build();
    public static final GTToolType HARD_HAMMER = builder("hammer").lang("%s Hammer").className("gtceu.tool.class.hammer", "Hammer").tooltip("item.gtceu.tool.hammer.tooltip").symbol('h').toolTag(CustomTags.CRAFTING_HAMMERS).toolTag(CustomTags.HAMMERS).harvestTag(CustomTags.MINEABLE_WITH_HAMMER).harvestTag(BlockTags.MINEABLE_WITH_PICKAXE).toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(2).attackDamage(1.0F).attackSpeed(-2.8F).behaviors(EntityDamageBehavior.HAMMER)).sound(GTSoundEntries.FORGE_HAMMER).toolClasses(PICKAXE).materialAmount(6 * GTValues.M).build();
    public static final GTToolType SOFT_MALLET = builder("mallet").lang("%s Soft Mallet").className("gtceu.tool.class.mallet", "Soft Mallet").tooltip("item.gtceu.tool.mallet.tooltip.0", "item.gtceu.tool.mallet.tooltip.1").symbol('r').toolTag(CustomTags.CRAFTING_MALLETS).toolTag(CustomTags.MALLETS).toolStats(b -> b.crafting().cannotAttack().attackSpeed(-2.4F).sneakBypassUse()).sound(GTSoundEntries.SOFT_MALLET_TOOL).materialAmount(6 * GTValues.M).build();
    public static final GTToolType WRENCH = builder("wrench").lang("%s Wrench").className("gtceu.tool.class.wrench", "Wrench").tooltip("item.gtceu.tool.wrench.tooltip").symbol('w').toolTag(CustomTags.CRAFTING_WRENCHES).toolTag(CustomTags.WRENCHES).toolTag(CustomTags.WRENCH).harvestTag(CustomTags.MINEABLE_WITH_WRENCH).toolStats(b -> b.blockBreaking().crafting().sneakBypassUse().attackDamage(1.0F).attackSpeed(-2.8F).behaviors(BlockRotatingBehavior.INSTANCE, EntityDamageBehavior.WRENCH, ToolModeSwitchBehavior.INSTANCE)).sound(GTSoundEntries.WRENCH_TOOL, true).materialAmount(4 * GTValues.M).build();
    public static final GTToolType FILE = builder("file").lang("%s File").className("gtceu.tool.class.file", "File").symbol('f').toolTag(CustomTags.CRAFTING_FILES).toolTag(CustomTags.FILES).toolStats(b -> b.crafting().damagePerCraftingAction(4).cannotAttack().attackSpeed(-2.4F)).sound(GTSoundEntries.FILE_TOOL).materialAmount(2 * GTValues.M).build();
    public static final GTToolType CROWBAR = builder("crowbar").lang("%s Crowbar").className("gtceu.tool.class.crowbar", "Crowbar").tooltip("item.gtceu.tool.crowbar.tooltip").symbol('c').toolTag(CustomTags.CRAFTING_CROWBARS).toolTag(CustomTags.CROWBARS).harvestTag(CustomTags.MINEABLE_WITH_CROWBAR).toolStats(b -> b.blockBreaking().crafting().attackDamage(2.0F).attackSpeed(-2.4F).sneakBypassUse().behaviors(RotateRailBehavior.INSTANCE)).sound(new ExistingSoundEntry(SoundEvents.ITEM_BREAK, SoundSource.BLOCKS), true).materialAmount(3 * GTValues.M / 2).build();
    public static final GTToolType SCREWDRIVER = builder("screwdriver").lang("%s Screwdriver").className("gtceu.tool.class.screwdriver", "Screwdriver").tooltip("item.gtceu.tool.screwdriver.tooltip").symbol('d').toolTag(CustomTags.CRAFTING_SCREWDRIVERS).toolTag(CustomTags.SCREWDRIVERS).toolStats(b -> b.crafting().damagePerCraftingAction(4).sneakBypassUse().cannotAttack().attackSpeed(3.0F).behaviors(EntityDamageBehavior.SCREWDRIVER)).sound(GTSoundEntries.SCREWDRIVER_TOOL).materialAmount(GTValues.M).build();
    public static final GTToolType MORTAR = builder("mortar").lang("%s Mortar").className("gtceu.tool.class.mortar", "Mortar").symbol('m').toolTag(CustomTags.CRAFTING_MORTARS).toolTag(CustomTags.MORTARS).toolStats(b -> b.crafting().damagePerCraftingAction(2).cannotAttack().attackSpeed(-2.4F)).sound(GTSoundEntries.MORTAR_TOOL).materialAmount(2 * GTValues.M).build();
    public static final GTToolType WIRE_CUTTER = builder("wire_cutter").lang("%s Wire Cutter").className("gtceu.tool.class.wire_cutter", "Wire Cutter").symbol('x').toolTag(CustomTags.CRAFTING_WIRE_CUTTERS).toolTag(CustomTags.WIRE_CUTTERS).harvestTag(CustomTags.MINEABLE_WITH_WIRE_CUTTER).toolStats(b -> b.blockBreaking().crafting().sneakBypassUse().damagePerCraftingAction(4).cannotAttack().attackSpeed(-2.4F)).sound(GTSoundEntries.WIRECUTTER_TOOL, true).materialAmount(4 * GTValues.M).build();
    public static final GTToolType KNIFE = builder("knife").lang("%s Knife").className("gtceu.tool.class.knife", "Knife").symbol('k').toolTag(CustomTags.CRAFTING_KNIVES).toolTag(CustomTags.KNIVES).harvestTag(CustomTags.MINEABLE_WITH_KNIFE).toolStats(b -> b.crafting().attacking().attackSpeed(3.0F)).toolClasses(SWORD).materialAmount(GTValues.M).build();
    public static final GTToolType BUTCHERY_KNIFE = builder("butchery_knife").lang("%s Butchery Knife").className("gtceu.tool.class.butchery_knife", "Butchery Knife").tooltip("item.gtceu.tool.butchery_knife.tooltip").actions(ToolActions.DEFAULT_SWORD_ACTIONS).toolTag(CustomTags.BUTCHERY_KNIVES).toolStats(b -> b.attacking().attackDamage(1.5F).attackSpeed(-1.3F).defaultEnchantment(Enchantments.MOB_LOOTING, 3)).materialAmount(4 * GTValues.M).build();
    public static final GTToolType PLUNGER = builder("plunger").lang("%s Plunger").className("gtceu.tool.class.plunger", "Plunger").tooltip("item.gtceu.tool.plunger.tooltip").toolTag(CustomTags.PLUNGERS).toolStats(b -> b.cannotAttack().attackSpeed(-2.4F).sneakBypassUse().behaviors(PlungerBehavior.INSTANCE)).sound(GTSoundEntries.PLUNGER_TOOL).build();
    public static final GTToolType SHEARS = builder("shears").lang("%s Shears").className("gtceu.tool.class.shears", "Shears").actions(ToolActions.DEFAULT_SHEARS_ACTIONS).toolTag(CustomTags.SHEARS).harvestTag(CustomTags.MINEABLE_WITH_SHEARS).toolStats(b -> b).build();
    public static final GTToolType DRILL_LV = drill(GTValues.LV, 1, 1, 0, 3.0F);
    public static final GTToolType DRILL_MV = drill(GTValues.MV, 1, 1, 2, 4.0F);
    public static final GTToolType DRILL_HV = drill(GTValues.HV, 2, 2, 4, 5.0F);
    public static final GTToolType DRILL_EV = drill(GTValues.EV, 3, 3, 6, 6.0F);
    public static final GTToolType DRILL_IV = drill(GTValues.IV, 4, 4, 8, 7.0F);
    public static final GTToolType CHAINSAW_LV = electric("lv_chainsaw", GTValues.LV, () -> TagPrefix.toolHeadChainsaw).idFormat("lv_%s_chainsaw").lang("%s Chainsaw (LV)").toolTag(ItemTags.AXES).toolTag(CustomTags.CHAINSAWS).harvestTag(BlockTags.MINEABLE_WITH_AXE).harvestTag(BlockTags.SWORD_EFFICIENT).harvestTag(BlockTags.MINEABLE_WITH_HOE).toolStats(b -> b.blockBreaking().efficiencyMultiplier(2.0F).attackDamage(5.0F).attackSpeed(-3.2F).brokenStack(powerUnit(GTValues.LV)).behaviors(HarvestIceBehavior.INSTANCE, DisableShieldBehavior.INSTANCE)).sound(GTSoundEntries.CHAINSAW_TOOL, true).toolClasses(AXE).build();
    public static final GTToolType WRENCH_LV = electricWrench(GTValues.LV, 2.0F);
    public static final GTToolType WRENCH_HV = electricWrench(GTValues.HV, 3.0F);
    public static final GTToolType WRENCH_IV = electricWrench(GTValues.IV, 4.0F);
    public static final GTToolType WIRE_CUTTER_LV = electricWireCutter(GTValues.LV);
    public static final GTToolType WIRE_CUTTER_HV = electricWireCutter(GTValues.HV);
    public static final GTToolType WIRE_CUTTER_IV = electricWireCutter(GTValues.IV);
    public static final GTToolType BUZZSAW = electric("buzzsaw", GTValues.LV, () -> TagPrefix.toolHeadBuzzSaw).lang("%s Buzzsaw (LV)").tooltip("item.gtceu.tool.buzzsaw.tooltip").toolTag(CustomTags.CRAFTING_SAWS).toolTag(CustomTags.SAWS).toolTag(CustomTags.BUZZSAWS).toolStats(b -> b.crafting().attackDamage(1.5F).attackSpeed(-3.2F).brokenStack(powerUnit(GTValues.LV))).sound(GTSoundEntries.CHAINSAW_TOOL, true).toolClasses(SAW).build();
    public static final GTToolType SCREWDRIVER_LV = electric("lv_screwdriver", GTValues.LV, () -> TagPrefix.toolHeadScrewdriver).idFormat("lv_%s_screwdriver").lang("%s Screwdriver (LV)").tooltip("item.gtceu.tool.lv_screwdriver.tooltip").toolTag(CustomTags.CRAFTING_SCREWDRIVERS).toolTag(CustomTags.SCREWDRIVERS).toolStats(b -> b.crafting().sneakBypassUse().cannotAttack().attackSpeed(3.0F).behaviors(EntityDamageBehavior.SCREWDRIVER).brokenStack(powerUnit(GTValues.LV))).sound(GTSoundEntries.SCREWDRIVER_TOOL).toolClasses(SCREWDRIVER).build();

    private static GTToolType drill(int tier, int column, int row, int layer, float durability) {
        String voltage = GTValues.VN[tier].toLowerCase(Locale.ROOT);
        return electric(voltage + "_drill", tier, () -> TagPrefix.toolHeadDrill).idFormat(voltage + "_%s_drill").lang("%s Drill (" + GTValues.VN[tier] + ")").className("gtceu.tool.class.drill", "Drill")
                .toolTag(CustomTags.DRILLS).toolTag(ItemTags.PICKAXES).toolTag(ItemTags.SHOVELS).toolTag(ItemTags.HOES).toolTag(ItemTags.CLUSTER_MAX_HARVESTABLES)
                .harvestTag(BlockTags.MINEABLE_WITH_PICKAXE).harvestTag(BlockTags.MINEABLE_WITH_SHOVEL).harvestTag(BlockTags.MINEABLE_WITH_HOE)
                .toolStats(b -> b.blockBreaking().aoe(column, row, layer).attackDamage(1.0F).attackSpeed(-3.2F).durabilityMultiplier(durability).brokenStack(powerUnit(tier)).behaviors(AoEBehavior.INSTANCE))
                .sound(GTSoundEntries.DRILL_TOOL, true).toolClasses(PICKAXE, SHOVEL, HOE).build();
    }

    private static GTToolType electricWrench(int tier, float efficiency) {
        String voltage = GTValues.VN[tier].toLowerCase(Locale.ROOT);
        return electric(voltage + "_wrench", tier, () -> TagPrefix.toolHeadWrench).idFormat(voltage + "_%s_wrench").lang("%s Wrench (" + GTValues.VN[tier] + ")").tooltip("item.gtceu.tool.wrench.tooltip")
                .toolTag(CustomTags.CRAFTING_WRENCHES).toolTag(CustomTags.WRENCHES).toolTag(CustomTags.WRENCH).harvestTag(CustomTags.MINEABLE_WITH_WRENCH)
                .toolStats(b -> b.blockBreaking().crafting().sneakBypassUse().efficiencyMultiplier(efficiency).attackDamage(1.0F).attackSpeed(-2.8F).behaviors(BlockRotatingBehavior.INSTANCE, EntityDamageBehavior.WRENCH, ToolModeSwitchBehavior.INSTANCE).brokenStack(powerUnit(tier)))
                .sound(GTSoundEntries.WRENCH_TOOL, true).toolClasses(WRENCH).build();
    }

    private static GTToolType electricWireCutter(int tier) {
        String voltage = GTValues.VN[tier].toLowerCase(Locale.ROOT);
        return electric(voltage + "_wirecutter", tier, () -> TagPrefix.toolHeadWireCutter).idFormat(voltage + "_%s_wire_cutter").lang("%s Wire Cutter (" + GTValues.VN[tier] + ")")
                .toolTag(CustomTags.CRAFTING_WIRE_CUTTERS).toolTag(CustomTags.WIRE_CUTTERS).harvestTag(CustomTags.MINEABLE_WITH_WIRE_CUTTER)
                .toolStats(b -> b.blockBreaking().crafting().sneakBypassUse().damagePerCraftingAction(4).cannotAttack().attackSpeed(-2.4F).brokenStack(powerUnit(tier)))
                .sound(GTSoundEntries.WIRECUTTER_TOOL, true).toolClasses(WIRE_CUTTER).build();
    }

    private static Builder electric(String name, int tier, Supplier<TagPrefix> toolHead) {
        return builder(name).electric(tier).toolHead(toolHead);
    }

    private static Supplier<ItemStack> powerUnit(int tier) {
        return () -> GTItems.powerUnit(tier).get().getDefaultInstance();
    }

    public final int index;
    public final String name;
    public final String idFormat;
    public final String descriptionId;
    @Nullable
    public final String englishName;
    @Nullable
    public final String classKey;
    @Nullable
    public final String classEnglishName;
    public final String[] tooltipKeys;
    public final List<TagKey<Item>> itemTags;
    public final List<TagKey<Block>> harvestTags;
    public final Set<GTToolType> toolClasses;
    public final Set<ToolAction> toolActions;
    private final TagKey<Block>[] effectiveHarvestTags;
    private final boolean actsAsSword;
    public final ResourceLocation modelLocation;
    @Nullable
    public final SoundEntry soundEntry;
    public final boolean playSoundOnBlockDestroy;
    public final char symbol;
    public final long materialAmount;
    public final ToolDefinition toolDefinition;
    public final ToolConstructor constructor;
    public final int electricTier;
    @Nullable
    private final Supplier<TagPrefix> toolHead;
    private Component usableAs;

    @SuppressWarnings("unchecked")
    private GTToolType(Builder b) {
        if (BY_NAME.containsKey(b.name)) {
            throw new IllegalArgumentException("Duplicate tool type " + b.name);
        }
        if (b.symbol != ' ' && BY_SYMBOL.containsKey(b.symbol)) {
            throw new IllegalArgumentException("Symbol " + b.symbol + " has been taken by " + BY_SYMBOL.get(b.symbol).name);
        }
        this.index = ALL.size();
        this.name = b.name;
        this.idFormat = b.idFormat;
        this.descriptionId = "item.gtceu.tool." + b.name;
        this.englishName = b.englishName;
        this.classKey = b.classKey;
        this.classEnglishName = b.classEnglishName;
        this.tooltipKeys = b.tooltipKeys;
        this.itemTags = ImmutableList.copyOf(b.itemTags);
        this.harvestTags = ImmutableList.copyOf(b.harvestTags);
        var classes = new ReferenceLinkedOpenHashSet<GTToolType>();
        classes.add(this);
        var actions = new LinkedHashSet<>(b.actions);
        var harvest = new LinkedHashSet<>(b.harvestTags);
        for (GTToolType parent : b.toolClasses) {
            classes.addAll(parent.toolClasses);
            actions.addAll(parent.toolActions);
            Collections.addAll(harvest, parent.effectiveHarvestTags);
        }
        this.toolClasses = Collections.unmodifiableSet(classes);
        this.toolActions = ImmutableSet.copyOf(actions);
        this.effectiveHarvestTags = harvest.toArray(TagKey[]::new);
        this.actsAsSword = b.name.equals("sword") || classes.stream().anyMatch(t -> t.name.equals("sword"));
        this.modelLocation = b.modelLocation;
        this.soundEntry = b.sound;
        this.playSoundOnBlockDestroy = b.playSoundOnBlockDestroy;
        this.symbol = b.symbol;
        this.materialAmount = b.materialAmount;
        this.toolDefinition = b.toolStats == null ? ToolDefinition.builder().build() : b.toolStats;
        this.constructor = b.constructor;
        this.electricTier = b.electricTier;
        this.toolHead = b.toolHead;
        ALL.add(this);
        BY_NAME.put(name, this);
        if (symbol != ' ') BY_SYMBOL.put(symbol, this);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    @UnmodifiableView
    public static List<GTToolType> all() {
        return ALL_VIEW;
    }

    @Nullable
    public static GTToolType byName(String name) {
        return BY_NAME.get(name);
    }

    @Nullable
    public static GTToolType fromSymbol(char symbol) {
        return BY_SYMBOL.get(symbol);
    }

    public static boolean isToolSymbol(char symbol) {
        return BY_SYMBOL.containsKey(symbol);
    }

    public boolean isElectric() {
        return electricTier != NOT_ELECTRIC;
    }

    public boolean actsAs(GTToolType type) {
        return toolClasses.contains(type);
    }

    public boolean actsAsSword() {
        return actsAsSword;
    }

    public TagKey<Item> mainTag() {
        return itemTags.getFirst();
    }

    @Nullable
    public TagPrefix toolHead() {
        return toolHead == null ? null : toolHead.get();
    }

    public boolean isEffectiveOn(BlockState state) {
        for (TagKey<Block> tag : effectiveHarvestTags) {
            if (state.is(tag)) return true;
        }
        return false;
    }

    public boolean is(ItemStack itemStack) {
        return ToolHelper.is(itemStack, this);
    }

    public String getUnlocalizedName() {
        return descriptionId;
    }

    public Component usableAs() {
        if (usableAs == null) {
            MutableComponent component = Component.empty();
            boolean first = true;
            for (GTToolType type : toolClasses) {
                if (type.classKey == null) continue;
                if (!first) component.append(", ");
                component.append(Component.translatable(type.classKey));
                first = false;
            }
            usableAs = component;
        }
        return usableAs;
    }

    @Override
    public String toString() {
        return name;
    }

    @FunctionalInterface
    public interface ToolConstructor {

        IGTTool apply(GTToolType type, MaterialToolTier tier, Material material, Item.Properties properties);
    }

    public static final class Builder {

        private final String name;
        private String idFormat;
        @Nullable
        private String englishName;
        @Nullable
        private String classKey;
        @Nullable
        private String classEnglishName;
        private String[] tooltipKeys = new String[0];
        private final List<TagKey<Item>> itemTags = new ArrayList<>();
        private final List<TagKey<Block>> harvestTags = new ArrayList<>();
        private final List<GTToolType> toolClasses = new ArrayList<>();
        private final Set<ToolAction> actions = new LinkedHashSet<>();
        @Nullable
        private ToolDefinition toolStats;
        private long materialAmount;
        private int electricTier = NOT_ELECTRIC;
        private char symbol = ' ';
        private ToolConstructor constructor = GTToolItem::create;
        private ResourceLocation modelLocation;
        @Nullable
        private SoundEntry sound;
        private boolean playSoundOnBlockDestroy;
        @Nullable
        private Supplier<TagPrefix> toolHead;

        private Builder(String name) {
            this.name = name;
            this.idFormat = "%s_" + name;
            this.modelLocation = GTCEu.id("item/tools/" + name);
        }

        public Builder idFormat(String idFormat) {
            this.idFormat = idFormat;
            return this;
        }

        public Builder lang(String englishName) {
            this.englishName = englishName;
            return this;
        }

        public Builder className(String classKey, String englishName) {
            this.classKey = classKey;
            this.classEnglishName = englishName;
            return this;
        }

        public Builder tooltip(String... keys) {
            this.tooltipKeys = keys;
            return this;
        }

        @SafeVarargs
        public final Builder toolTag(TagKey<Item>... tags) {
            Collections.addAll(itemTags, tags);
            return this;
        }

        @SafeVarargs
        public final Builder harvestTag(TagKey<Block>... tags) {
            Collections.addAll(harvestTags, tags);
            return this;
        }

        public Builder toolClasses(GTToolType... classes) {
            Collections.addAll(toolClasses, classes);
            return this;
        }

        public Builder actions(Set<ToolAction> actions) {
            this.actions.addAll(actions);
            return this;
        }

        public Builder toolStats(UnaryOperator<ToolDefinition.Builder> builder) {
            this.toolStats = builder.apply(ToolDefinition.builder()).build();
            return this;
        }

        public Builder sound(SoundEntry sound) {
            return sound(sound, false);
        }

        public Builder sound(SoundEntry sound, boolean playSoundOnBlockDestroy) {
            this.sound = sound;
            this.playSoundOnBlockDestroy = playSoundOnBlockDestroy;
            return this;
        }

        public Builder electric(int tier) {
            this.electricTier = tier;
            return this;
        }

        public Builder toolHead(Supplier<TagPrefix> toolHead) {
            this.toolHead = toolHead;
            return this;
        }

        public Builder materialAmount(long materialAmount) {
            this.materialAmount = materialAmount;
            return this;
        }

        public Builder symbol(char symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder constructor(ToolConstructor constructor) {
            this.constructor = constructor;
            return this;
        }

        public Builder modelLocation(ResourceLocation modelLocation) {
            this.modelLocation = modelLocation;
            return this;
        }

        public GTToolType build() {
            return new GTToolType(this);
        }
    }
}
