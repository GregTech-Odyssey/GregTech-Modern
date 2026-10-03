package com.gregtechceu.gtceu.common.machine.trait.miner;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.recipe.content.ContentRoll;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.recipesearch.IntLongMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
public class LargeMinerLogic extends MinerLogic {

    private static final int CHUNK_LENGTH = 16;
    @Setter
    private int voltageTier;
    @Setter
    private int overclockAmount = 0;
    @SaveToDisk
    private boolean isChunkMode;

    /**
     * Creates the logic for multiblock ore block miners
     *
     * @param machine       the {@link IRecipeLogicMachine} this logic belongs to
     * @param fortune       the fortune amount to apply when mining ores
     * @param speed         the speed in ticks per block mined
     * @param maximumRadius the maximum radius (square shaped) the miner can mine in
     */
    public LargeMinerLogic(IRecipeLogicMachine machine, int fortune, int speed, int maximumRadius) {
        super(machine, fortune, speed, maximumRadius);
    }

    @Override
    public void initPos(@NotNull BlockPos pos, int currentRadius) {
        if (!isChunkMode) {
            super.initPos(pos, currentRadius);
        } else {
            Direction dir = super.getDir();
            ServerLevel world = (ServerLevel) this.getMachine().getLevel();
            ChunkAccess origin = world.getChunk(pos);
            ChunkPos startPos = (world.getChunk(origin.getPos().x - currentRadius / CHUNK_LENGTH, origin.getPos().z - currentRadius / CHUNK_LENGTH)).getPos();
            x = startPos.getMinBlockX();
            if (dir == Direction.UP) {
                y = pos.getY() + 1;
            } else {
                y = pos.getY() - 1;
            }
            z = startPos.getMinBlockZ();
            startX = startPos.getMinBlockX();
            startY = pos.getY();
            startZ = startPos.getMinBlockZ();
            mineX = startPos.getMinBlockX();
            if (dir == Direction.UP) {
                mineY = pos.getY() + 1;
            } else {
                mineY = pos.getY() - 1;
            }
            mineZ = startPos.getMinBlockZ();
            if (dir == Direction.UP) {
                pipeY = pos.getY() + 1;
            } else {
                pipeY = pos.getY() - 1;
            }
            onRemove();
        }
    }

    /** 非精准模式时做后处理：把矿石方块按研磨机配方加工成粉碎矿。 */
    @Override
    protected boolean hasPostProcessing() {
        return !isSilkTouchActive();
    }

    private final IntLongMap processingSearchMap = new IntLongMap();

    @Override
    protected boolean doPostProcessing(List<ItemStack> blockDrops, BlockState blockState) {
        processingSearchMap.clear();
        return miner.getRecipeType().search(recipe -> {
            var outputs = recipe.itemOutputs;
            for (int i = 0, size = outputs.size(); i < size; i++) {
                int chance = outputs.chance(i);
                if (chance == 0) continue;
                long amount = chance == ContentList.MAX_CHANCE ? outputs.amount(i) : ContentRoll.roll(outputs.amount(i), outputs.rollUnit(i), recipe.chanceFunction.getBoostedChance(chance, outputs.boost(i), recipe.tier, recipe.tier), ContentRoll.RNG);
                if (amount <= 0 || !(outputs.outputKey(i) instanceof AEItemKey key)) continue;
                blockDrops.add(Keys.toStack(key, Keys.multiply(amount, ChemicalHelper.getPrefix(key.getItem()) == TagPrefix.crushed ? 3 : 1)));
            }
            return !blockDrops.isEmpty();
        }, processingSearchMap, blockState.getBlock().asItem().getDefaultInstance());
    }

    public void setChunkMode(boolean isChunkMode) {
        if (!isWorking()) {
            this.isChunkMode = isChunkMode;
            if (!getMachine().isRemote()) {
                resetArea(true);
            }
        }
    }

    @Override
    public BlockPos getMiningPos() {
        return getMachine().getPos().relative(getMachine().getFrontFacing().getOpposite());
    }
}
