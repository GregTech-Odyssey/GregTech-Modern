package com.gregtechceu.gtceu.common.machine.multiblock.primitive;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.ITickSubscription;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblockpro.ParamKey;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Piece;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Size;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Structure;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Symbols;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.item.tool.behavior.LighterBehavior;
import com.gregtechceu.gtceu.core.ILevel;
import com.gregtechceu.gtceu.data.recipe.CustomTags;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.gto.datasynclib.annotations.SyncToClient;
import com.gto.datasynclib.datastream.DataComponentKey;
import com.gto.fastcollection.fastutil.OpenCacheHashSet;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.*;

public class CharcoalPileIgniterMachine extends WorkableMultiblockMachine implements IInteractedMachine {

    private static final DataComponentKey<Long2BooleanMap> LOG_POS = DataComponentKey.create("logPos", DataComponentKey.long2BooleanMapBuilder(Long2BooleanOpenHashMap::new));

    private static final int MIN_RADIUS = 1;
    private static final int MIN_DEPTH = 2;
    private static final int MAX_RADIUS = 5;
    private static final int MAX_DEPTH = 5;

    public static final ParamKey LEFT_DIST = ParamKey.of("gtceu.multiblock.charcoal_pile_igniter.left", "gtceu.multiblock.charcoal_pile_igniter.left.desc");
    public static final ParamKey RIGHT_DIST = ParamKey.of("gtceu.multiblock.charcoal_pile_igniter.right", "gtceu.multiblock.charcoal_pile_igniter.right.desc");
    public static final ParamKey FRONT_DIST = ParamKey.of("gtceu.multiblock.charcoal_pile_igniter.front", "gtceu.multiblock.charcoal_pile_igniter.front.desc");
    public static final ParamKey BACK_DIST = ParamKey.of("gtceu.multiblock.charcoal_pile_igniter.back", "gtceu.multiblock.charcoal_pile_igniter.back.desc");
    public static final ParamKey HEIGHT = ParamKey.of("gtceu.multiblock.charcoal_pile_igniter.height", "gtceu.multiblock.charcoal_pile_igniter.height.desc");

    private final Collection<BlockPos> logPos = new OpenCacheHashSet<>();

    @SyncToClient
    private int lDist = 0;
    @SyncToClient
    private int rDist = 0;
    @SyncToClient
    private int bDist = 0;
    @SyncToClient
    private int fDist = 0;
    @SyncToClient
    private int hDist = 0;

    private boolean hasAir = false;
    private TickableSubscription particleSubscription;

    public CharcoalPileIgniterMachine(MetaMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public void onStructureFormedClient() {
        super.onStructureFormedClient();
        particleSubscription = subscribeClientTick(particleSubscription, this::particleTick);
    }

    @Override
    public void onStructureInvalidClient() {
        super.onStructureInvalidClient();
        particleSubscription = ITickSubscription.unsubscribe(particleSubscription);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        updateDimensions();
        hasAir = false;
        if (getMultiblockState().getMatchContext().containsKey(LOG_POS)) {
            Long2BooleanMap logPositions = getMultiblockState().getMatchContext().get(LOG_POS);
            for (var entry : logPositions.long2BooleanEntrySet()) {
                if (entry.getBooleanValue()) {
                    logPos.add(BlockPos.of(entry.getLongKey()));
                } else {
                    hasAir = true;
                }
            }
        }
        this.getRecipeLogic().setDuration(Math.max(1, (int) Math.sqrt(logPos.size() * 240_000)));
    }

    @Override
    @NotNull
    public CharcoalRecipeLogic createRecipeLogic(Object @NotNull... args) {
        return new CharcoalRecipeLogic(this);
    }

    @Override
    public @NotNull CharcoalRecipeLogic getRecipeLogic() {
        return (CharcoalRecipeLogic) super.getRecipeLogic();
    }

    @Override
    public boolean isActive() {
        return recipeLogic.isWorking();
    }

    @Override
    public boolean isWorkingEnabled() {
        return true;
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {}

    public static Structure structure(MultiblockMachineDefinition definition) {
        var floor = Predicates.blocks(Blocks.BRICKS);
        var wallBelow = new TraceabilityPredicate(state -> ILevel.asyncGetBlockState(state.world, state.getPos().below()).is(CustomTags.CHARCOAL_PILE_IGNITER_WALLS), null, null);
        var symbols = Symbols.create()
                .where('S', Predicates.controller(definition))
                .wherePart('B', Predicates.blocks(Blocks.BRICKS))
                .where('W', Predicates.blockTag(CustomTags.CHARCOAL_PILE_IGNITER_WALLS))
                .where('L', logPredicate())
                .where('A', Predicates.any());
        return Structure.root(Piece.sized(CharcoalPileIgniterMachine::pile))
                .symbols(symbols)
                .measure(m -> m.param(LEFT_DIST).toward(LEFT).until(wallBelow).range(MIN_RADIUS, MAX_RADIUS))
                .measure(m -> m.param(RIGHT_DIST).toward(RIGHT).until(wallBelow).range(MIN_RADIUS, MAX_RADIUS))
                .measure(m -> m.param(FRONT_DIST).toward(FRONT).until(wallBelow).range(MIN_RADIUS, MAX_RADIUS))
                .measure(m -> m.param(BACK_DIST).toward(BACK).until(wallBelow).range(MIN_RADIUS, MAX_RADIUS))
                .measure(m -> m.param(HEIGHT).toward(DOWN).until(floor).range(MIN_DEPTH, MAX_DEPTH))
                .require(size -> Math.abs(size.get(LEFT_DIST) - size.get(RIGHT_DIST)) <= 1 && Math.abs(size.get(FRONT_DIST) - size.get(BACK_DIST)) <= 1)
                .build();
    }

    private static Piece pile(Size size) {
        int width = size.get(LEFT_DIST) + size.get(RIGHT_DIST) + 1;
        int depth = size.get(FRONT_DIST) + size.get(BACK_DIST) + 1;
        int centerChar = size.get(RIGHT_DIST);
        int centerRow = size.get(BACK_DIST);
        var floorLayer = new String[depth];
        var wallLayer = new String[depth];
        var ceilingLayer = new String[depth];
        for (int j = 0; j < depth; j++) {
            var f = new StringBuilder(width);
            var m = new StringBuilder(width);
            var c = new StringBuilder(width);
            for (int i = 0; i < width; i++) {
                if (i == 0 || i == width - 1 || j == 0 || j == depth - 1) {
                    f.append('A');
                    m.append((i == 0 || i == width - 1) && (j == 0 || j == depth - 1) ? 'A' : 'W');
                    c.append('A');
                } else {
                    f.append('B');
                    m.append('L');
                    c.append(i == centerChar && j == centerRow ? 'S' : 'W');
                }
            }
            floorLayer[j] = f.toString();
            wallLayer[j] = m.toString();
            ceilingLayer[j] = c.toString();
        }
        var builder = Piece.start(LEFT, FRONT, UP).aisle(floorLayer);
        for (int k = 0; k < size.get(HEIGHT) - 1; k++) builder.aisle(wallLayer);
        return builder.aisle(ceilingLayer).build();
    }

    protected static TraceabilityPredicate logPredicate() {
        return new TraceabilityPredicate(multiblockState -> {
            BlockState state = multiblockState.getBlockState();
            long pos = multiblockState.getPos().asLong();
            boolean log = state.is(BlockTags.LOGS_THAT_BURN);
            if (log || state.isAir()) {
                multiblockState.getMatchContext().getOrCreate(LOG_POS, Long2BooleanOpenHashMap::new).put(pos, log);
                return true;
            }
            return false;
            // copied from PredicateBlockTag to display the preview logs properly
        }, () -> BlockInfo.fromBlock(Blocks.OAK_WOOD), () -> BuiltInRegistries.BLOCK.getTag(BlockTags.LOGS_THAT_BURN)
                .stream()
                .flatMap(HolderSet.Named::stream)
                .map(Holder::value)
                .toArray(Block[]::new));
    }

    public void updateDimensions() {
        var assembly = getAssembly();
        if (assembly == null || !assembly.has(LEFT_DIST)) return;
        this.lDist = assembly.get(LEFT_DIST);
        this.rDist = assembly.get(RIGHT_DIST);
        this.fDist = assembly.get(FRONT_DIST);
        this.bDist = assembly.get(BACK_DIST);
        this.hDist = assembly.get(HEIGHT);
    }

    @OnlyIn(Dist.CLIENT)
    private void particleTick() {
        if (isActive()) {
            var pos = this.getPos();
            var facing = Direction.UP;
            float xPos = facing.getStepX() * 0.76F + pos.getX() + 0.25F + GTValues.RNG.nextFloat() / 2.0F;
            float yPos = facing.getStepY() * 0.76F + pos.getY() + 0.25F;
            float zPos = facing.getStepZ() * 0.76F + pos.getZ() + 0.25F + GTValues.RNG.nextFloat() / 2.0F;

            float ySpd = facing.getStepY() * 0.1F + 0.01F * GTValues.RNG.nextFloat();
            float horSpd = 0.03F * GTValues.RNG.nextFloat();
            float horSpd2 = 0.03F * GTValues.RNG.nextFloat();

            if (GTValues.RNG.nextFloat() < 0.1F) {
                getLevel().playLocalSound(xPos, yPos, zPos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.0F,
                        1.0F, false);
            }
            for (float xi = xPos - 1; xi <= xPos + 1; xi++) {
                for (float zi = zPos - 1; zi <= zPos + 1; zi++) {
                    if (GTValues.RNG.nextFloat() < .9F)
                        continue;
                    getLevel().addParticle(ParticleTypes.LARGE_SMOKE, xi, yPos, zi, horSpd, ySpd, horSpd2);
                }
            }
        }
    }

    private void convertLogBlocks() {
        Level level = getLevel();
        for (BlockPos pos : logPos) {
            level.setBlockAndUpdate(pos, GTBlocks.BRITTLE_CHARCOAL.getDefaultState());
        }
        logPos.clear();
    }

    @Override
    public InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                   BlockHitResult hit) {
        if (!isFormed() || hasAir) {
            return IInteractedMachine.super.onUse(state, level, pos, player, hand, hit);
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(CustomTags.TOOLS_IGNITER)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide && !isActive()) {
            return InteractionResult.SUCCESS;
        } else if (!isActive()) {
            boolean shouldActivate = false;
            if (stack.getItem() instanceof ComponentItem compItem) {
                for (var component : compItem.getComponents()) {
                    if (component instanceof LighterBehavior lighter && lighter.consumeFuel(player, stack)) {
                        shouldActivate = true;
                        break;
                    }
                }
            } else if (stack.isDamageableItem()) {
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                shouldActivate = true;
            } else {
                stack.shrink(1);
                shouldActivate = true;
            }

            if (shouldActivate) {
                getRecipeLogic().setStatus(RecipeLogic.WORKING);

                level.playSound(null, pos,
                        stack.is(Items.FIRE_CHARGE) ? SoundEvents.FIRECHARGE_USE : SoundEvents.FLINTANDSTEEL_USE,
                        SoundSource.BLOCKS, 1.0f, 1.0f);
                return InteractionResult.CONSUME;
            }
        }
        return IInteractedMachine.super.onUse(state, level, pos, player, hand, hit);
    }

    public static class CharcoalRecipeLogic extends RecipeLogic {

        private final CharcoalPileIgniterMachine machine;

        public CharcoalRecipeLogic(CharcoalPileIgniterMachine machine) {
            super(machine);
            this.machine = machine;
        }

        @Override
        public void serverTick() {
            super.serverTick();
            if (isWorking() && duration > 0) {
                if (++progress >= duration) {
                    progress = 0;
                    duration = 0;
                    this.machine.convertLogBlocks();
                    setStatus(IDLE);
                }
            }
        }

        public void setDuration(int max) {
            this.duration = max;
        }
    }
}
