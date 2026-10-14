package com.gregtechceu.gtceu.api.item.tool;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.ToolProperty;
import com.gregtechceu.gtceu.api.item.IDataComponentItem;
import com.gregtechceu.gtceu.api.item.IGTTool;
import com.gregtechceu.gtceu.api.item.tool.aoe.AoESymmetrical;
import com.gregtechceu.gtceu.api.item.tool.behavior.IToolBehavior;
import com.gregtechceu.gtceu.common.data.GTMaterialItems;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.DigDurabilityEnchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.IForgeShearable;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.event.ForgeEventFactory;

import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ToolHelper {

    public static final Map<TagKey<Item>, ItemStack> HAMMER_DROP = new Reference2ObjectOpenHashMap<>();

    private static final Map<Item, Set<GTToolType>> TAGGED_TOOL_TYPES = new ConcurrentHashMap<>();
    private static ItemStack silkTouchTool;

    private ToolHelper() {}

    @FunctionalInterface
    public interface AoEPredicate {

        boolean test(UseOnContext center, BlockPos pos);
    }

    public static ItemStack get(GTToolType toolType, Material material) {
        if (material.hasProperty(PropertyKey.TOOL)) {
            var entry = GTMaterialItems.TOOL_ITEMS.get(material, toolType);
            if (entry != null) return entry.get().get();
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack getPlain(GTToolType toolType, Material material) {
        var entry = GTMaterialItems.TOOL_ITEMS.get(material, toolType);
        if (entry == null) return ItemStack.EMPTY;
        IGTTool tool = entry.get();
        ItemStack stack = tool.getRaw();
        IDataComponentItem.set(stack, ToolData.HIDE_FLAGS, 2);
        ToolProperty toolProperty = material.getProperty(PropertyKey.TOOL);
        if (toolProperty != null) {
            for (var enchantment : Object2IntMaps.fastIterable(toolProperty.getEnchantments())) {
                if (stack.getItem().canApplyAtEnchantingTable(stack, enchantment.getKey())) {
                    stack.enchant(enchantment.getKey(), enchantment.getIntValue());
                }
            }
        }
        return stack;
    }

    public static boolean hasBehavior(ItemStack stack, IToolBehavior behavior) {
        return stack.getItem() instanceof IGTTool tool && tool.getToolStats().hasBehavior(behavior);
    }

    public static boolean isMagnetic(ItemStack tool) {
        return tool.getItem() instanceof IGTTool g && g.isMagnetic();
    }

    public static Set<GTToolType> getToolTypes(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof IGTTool tool) return tool.getToolType().toolClasses;
        if (stack.isEmpty()) return Collections.emptySet();
        Set<GTToolType> types = TAGGED_TOOL_TYPES.get(item);
        if (types == null) {
            var found = new ReferenceLinkedOpenHashSet<GTToolType>();
            for (GTToolType type : GTToolType.all()) {
                for (TagKey<Item> tag : type.itemTags) {
                    if (stack.is(tag)) {
                        found.add(type);
                        break;
                    }
                }
            }
            types = found.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(found);
            TAGGED_TOOL_TYPES.put(item, types);
        }
        return types;
    }

    public static void clearTagCache() {
        TAGGED_TOOL_TYPES.clear();
    }

    public static boolean is(ItemStack stack, GTToolType toolType) {
        return getToolTypes(stack).contains(toolType);
    }

    public static boolean isTool(ItemStack stack, GTToolType... toolTypes) {
        Set<GTToolType> types = getToolTypes(stack);
        for (GTToolType type : toolTypes) {
            if (types.contains(type)) return true;
        }
        return false;
    }

    public static boolean canUse(ItemStack stack) {
        return stack.getDamageValue() <= stack.getMaxDamage();
    }

    public static void damageItem(@NotNull ItemStack stack, @Nullable LivingEntity user) {
        damageItem(stack, user, 1);
    }

    public static void damageItem(@NotNull ItemStack stack, @Nullable LivingEntity user, int damage) {
        if (!(stack.getItem() instanceof IGTTool tool)) {
            if (user != null) stack.hurtAndBreak(damage, user, p -> {});
            return;
        }
        if (ToolData.isUnbreakable(stack)) return;
        if (user instanceof Player player && player.isCreative()) return;
        RandomSource random = user == null ? GTValues.RNG : user.getRandom();
        if (tool.isElectric()) {
            IElectricItem electricItem = GTCapabilityHelper.getElectricItem(stack);
            if (electricItem != null) {
                electricItem.discharge(damage * tool.getEnergyPerUse(stack),
                        tool.getElectricTier(), true, false, false);
                if (electricItem.getCharge() > 0 &&
                        random.nextInt(100) >= ConfigHolder.INSTANCE.tools.rngDamageElectricTools) {
                    return;
                }
            }
        }
        int unbreakingLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, stack);
        int negated = 0;
        for (int k = 0; unbreakingLevel > 0 && k < damage; k++) {
            if (DigDurabilityEnchantment.shouldIgnoreDurabilityDrop(stack, unbreakingLevel, random)) negated++;
        }
        damage -= negated;
        if (damage <= 0) return;
        int newDurability = stack.getDamageValue() + damage;
        if (user instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(serverPlayer, stack, newDurability);
        }
        stack.setDamageValue(newDurability);
        if (newDurability > stack.getMaxDamage()) {
            if (user instanceof Player player) {
                player.awardStat(Stats.ITEM_BROKEN.get(stack.getItem()));
            }
            if (user != null) {
                user.breakItem(stack);
                user.broadcastBreakEvent(user.isUsingItem() ? user.getUsedItemHand() : InteractionHand.MAIN_HAND);
            }
            stack.shrink(1);
        }
    }

    public static void damageItemWhenCrafting(@NotNull ItemStack stack, @Nullable LivingEntity entity) {
        int damage = 2;
        if (stack.getItem() instanceof IGTTool tool) {
            damage = tool.getToolStats().damagePerCraft();
        } else if (stack.getTags().anyMatch(s -> s.location().getPath().startsWith("tool") ||
                s.location().getPath().startsWith("crafting_tool"))) {
                    damage = 1;
                }
        damageItem(stack, entity, damage);
    }

    public static void playToolSound(GTToolType toolType, ServerPlayer player) {
        if (toolType != null && toolType.soundEntry != null) {
            toolType.soundEntry.playOnServer(player.level(), player.blockPosition());
        }
    }

    public static void onActionDone(@Nullable Player player, @NotNull ItemStack stack, @NotNull Level level, @NotNull Vec3 pos) {
        IGTTool tool = (IGTTool) stack.getItem();
        damageItem(stack, player);
        if (tool.getSound() != null && IGTTool.canPlaySound(player)) {
            level.playSound(player, pos.x, pos.y, pos.z, tool.getSound().getMainEvent(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    public static AoESymmetrical getMaxAoEDefinition(ItemStack stack) {
        return stack.getItem() instanceof IGTTool tool ? tool.getToolStats().aoe : AoESymmetrical.ZERO;
    }

    public static AoESymmetrical getAoEDefinition(ItemStack stack) {
        AoESymmetrical max = getMaxAoEDefinition(stack);
        if (max.isZero()) return max;
        AoESymmetrical selected = IDataComponentItem.get(stack, AoESymmetrical.KEY);
        return selected == null ? max : selected.clampTo(max);
    }

    public static List<BlockPos> iterateAoE(AoESymmetrical aoe, UseOnContext context, AoEPredicate predicate) {
        Player player = context.getPlayer();
        Direction hitFace = context.getClickedFace();
        ItemStack stack = context.getItemInHand();
        Direction playerFacing = player != null ? player.getDirection() : Direction.NORTH;
        Direction depthDirection = hitFace.getOpposite();
        Direction topDirection = Direction.UP;
        Direction sideDirection = hitFace;
        int rowStart = aoe.row() == 0 ? 0 : -1;
        int rowEnd = aoe.row() == 0 ? 0 : aoe.row() * 2 - 1;
        if (hitFace.getAxis().isVertical()) {
            topDirection = playerFacing;
            sideDirection = playerFacing;
            rowStart = -aoe.row();
            rowEnd = aoe.row();
        }
        sideDirection = sideDirection.getClockWise();
        BlockPos center = context.getClickedPos();
        var cursor = new BlockPos.MutableBlockPos();
        var face = new BlockPos.MutableBlockPos();
        List<BlockPos> positions = new ArrayList<>();
        for (int depth = 0; depth <= aoe.layer(); depth++) {
            for (int top = rowEnd; top >= rowStart; top--) {
                for (int side = -aoe.column(); side <= aoe.column(); side++) {
                    cursor.set(center).move(depthDirection, depth).move(topDirection, top).move(sideDirection, side);
                    if (player != null && !player.mayUseItemAt(face.setWithOffset(cursor, hitFace), hitFace, stack)) {
                        continue;
                    }
                    if (predicate.test(context, cursor)) {
                        positions.add(cursor.immutable());
                    }
                }
            }
        }
        return positions;
    }

    public static List<BlockPos> getHarvestableBlocks(AoESymmetrical aoe, UseOnContext context) {
        return iterateAoE(aoe, context, ToolHelper::isBlockAoEHarvestable);
    }

    public static List<BlockPos> getHarvestableBlocks(ItemStack stack, Player player) {
        AoESymmetrical aoe = getAoEDefinition(stack);
        if (aoe.isZero()) return Collections.emptyList();
        BlockHitResult hitResult = getPlayerDefaultRaytrace(player);
        if (hitResult.getType() != BlockHitResult.Type.BLOCK) return Collections.emptyList();
        return getHarvestableBlocks(aoe, new UseOnContext(player, InteractionHand.MAIN_HAND, hitResult));
    }

    private static boolean isBlockAoEHarvestable(UseOnContext center, BlockPos pos) {
        Level level = center.getLevel();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return false;
        BlockPos centerPos = center.getClickedPos();
        if (hardness - level.getBlockState(centerPos).getDestroySpeed(level, centerPos) > 8) return false;
        ItemStack stack = center.getItemInHand();
        return stack.getItem().isCorrectToolForDrops(stack, state);
    }

    public static boolean areaOfEffectBlockBreakRoutine(ItemStack stack, ServerPlayer player, BlockPos targeted) {
        int remainingUses = stack.getMaxDamage() - stack.getDamageValue();
        var harvestableBlocks = getHarvestableBlocks(stack, player);
        if (harvestableBlocks.isEmpty()) return false;
        boolean electric = stack.getItem() instanceof IGTTool tool && tool.isElectric();
        for (BlockPos pos : harvestableBlocks) {
            if (!breakBlockRoutine(player, stack, pos, pos.equals(targeted))) return true;
            remainingUses--;
            if (!electric && remainingUses == 0) return true;
            if (!ItemStack.isSameItem(player.getMainHandItem(), stack)) return true;
        }
        return true;
    }

    public static void applyHammerDropConversion(ServerLevel world, BlockPos pos, ItemStack tool, BlockState state,
                                                 @Nullable BlockEntity blockEntity, @Nullable Entity entity,
                                                 List<ItemStack> drops, int fortune, float dropChance,
                                                 RandomSource random) {
        if (HAMMER_DROP.isEmpty() || !is(tool, GTToolType.HARD_HAMMER)) return;
        for (ItemStack silkTouchDrop : getSilkTouchDrop(world, pos, state, blockEntity, entity)) {
            Item item = silkTouchDrop.getItem();
            if (item == Items.AIR) continue;
            for (var tagKey : item.builtInRegistryHolder().tags) {
                ItemStack out = HAMMER_DROP.get(tagKey);
                if (out != null) {
                    drops.clear();
                    drops.add(out.copy());
                    return;
                }
            }
        }
    }

    public static boolean breakBlockRoutine(ServerPlayer player, ItemStack tool, BlockPos pos, boolean playSound) {
        if (is(tool, GTToolType.SHEARS) && shearBlockRoutine(player, tool, pos) == 0) return false;
        Level world = player.level();
        if (!onBlockBreakEvent(world, player.gameMode.getGameModeForPlayer(), player, pos)) return false;
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        BlockEntity tile = world.getBlockEntity(pos);
        if (block instanceof GameMasterBlock && !player.canUseGameMasterBlocks()) {
            world.sendBlockUpdated(pos, state, state, 3);
            return false;
        }
        if (player.blockActionRestricted(world, pos, player.gameMode.getGameModeForPlayer())) return false;
        if (player.isCreative()) return removeBlockRoutine(state, world, player, pos, playSound);
        world.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        ItemStack copiedTool = tool.copy();
        boolean canHarvest = player.hasCorrectToolForDrops(state);
        if (!tool.isEmpty()) {
            tool.mineBlock(world, state, pos, player);
            if (tool.isEmpty() && !copiedTool.isEmpty()) {
                onPlayerDestroyItem(player, copiedTool, InteractionHand.MAIN_HAND);
            }
        }
        boolean successful = removeBlockRoutine(null, world, player, pos, playSound);
        if (successful && canHarvest) {
            block.playerDestroy(world, player, pos, state, tile, copiedTool);
        }
        return successful;
    }

    public static boolean onBlockBreakEvent(Level level, GameType gameType, ServerPlayer player, BlockPos pos) {
        return ForgeHooks.onBlockBreakEvent(level, gameType, player, pos) != -1;
    }

    public static void onPlayerDestroyItem(Player player, ItemStack stack, InteractionHand hand) {
        ForgeEventFactory.onPlayerDestroyItem(player, stack, hand);
    }

    public static boolean removeBlockRoutine(@Nullable BlockState state, Level world, ServerPlayer player, BlockPos pos,
                                             boolean playSound) {
        state = state == null ? world.getBlockState(pos) : state;
        state.getBlock().playerWillDestroy(world, pos, state, player);
        boolean successful = world.removeBlock(pos, false);
        if (playSound) world.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        if (successful) state.getBlock().destroy(world, pos, state);
        return successful;
    }

    public static double getPlayerBlockReach(@NotNull Player player) {
        return player.getBlockReach();
    }

    public static BlockHitResult getPlayerDefaultRaytrace(@NotNull Player player) {
        return entityPickBlock(player, getPlayerBlockReach(player), 1.0f, false);
    }

    public static BlockHitResult entityPickBlock(Entity entity, double hitDistance, float partialTicks, boolean hitFluids) {
        Vec3 eyePos = entity.getEyePosition(partialTicks);
        Vec3 lookVec = entity.getViewVector(partialTicks);
        Vec3 maxDistance = eyePos.add(lookVec.x * hitDistance, lookVec.y * hitDistance, lookVec.z * hitDistance);
        return entity.level().clip(new ClipContext(eyePos, maxDistance, ClipContext.Block.OUTLINE,
                hitFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, entity));
    }

    public static boolean isToolEffective(BlockState state, GTToolType toolType, int harvestLevel) {
        return toolType.isEffectiveOn(state) && isCorrectTierForDrops(state, harvestLevel);
    }

    public static boolean isCorrectTierForDrops(BlockState state, int harvestLevel) {
        return TierSortingRegistry.isCorrectTierForDrops(getTier(harvestLevel), state);
    }

    private static Tier getTier(int harvestLevel) {
        List<Tier> tiers = TierSortingRegistry.getSortedTiers();
        for (int i = tiers.size() - 1; i >= 0; i--) {
            Tier tier = tiers.get(i);
            if (tier.getLevel() == harvestLevel) return tier;
        }
        return Tiers.WOOD;
    }

    public static int shearBlockRoutine(ServerPlayer player, ItemStack tool, BlockPos pos) {
        if (player.isCreative()) return -1;
        Level world = player.serverLevel();
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof IForgeShearable shearable) || !shearable.isShearable(tool, world, pos)) {
            return -1;
        }
        List<ItemStack> shearedDrops = shearable.onSheared(player, tool, world, pos,
                tool.getEnchantmentLevel(Enchantments.BLOCK_FORTUNE));
        boolean relocate = isMagnetic(tool);
        for (ItemStack stack : shearedDrops) {
            if (relocate && player.addItem(stack)) continue;
            double xo = world.random.nextFloat() * 0.7F + 0.15D;
            double yo = world.random.nextFloat() * 0.7F + 0.15D;
            double zo = world.random.nextFloat() * 0.7F + 0.15D;
            ItemEntity entityItem = new ItemEntity(world, pos.getX() + xo, pos.getY() + yo, pos.getZ() + zo, stack);
            entityItem.setDefaultPickUpDelay();
            world.addFreshEntity(entityItem);
        }
        damageItem(tool, player, 1);
        player.awardStat(Stats.BLOCK_MINED.get((Block) shearable));
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        return tool.isEmpty() ? 0 : 1;
    }

    @NotNull
    public static List<ItemStack> getSilkTouchDrop(ServerLevel world, BlockPos origin, @NotNull BlockState state,
                                                   @Nullable BlockEntity blockEntity, @Nullable Entity entity) {
        ItemStack tool = silkTouchTool;
        if (tool == null) {
            tool = GTMaterialItems.TOOL_ITEMS.get(GTMaterials.Neutronium, GTToolType.PICKAXE).get().get();
            tool.enchant(Enchantments.SILK_TOUCH, 1);
            silkTouchTool = tool;
        }
        return state.getDrops(new LootParams.Builder(world).withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(origin))
                .withParameter(LootContextParams.TOOL, tool)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, entity));
    }
}
