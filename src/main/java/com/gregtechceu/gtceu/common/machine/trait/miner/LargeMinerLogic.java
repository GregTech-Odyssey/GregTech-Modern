package com.gregtechceu.gtceu.common.machine.trait.miner;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

import com.gto.datasynclib.annotations.SaveToDisk;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

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

    private int getDropCountMultiplier() {
        return 5;
    }

    /**
     * 大型采矿机自带时运 5，与附魔槽里的时运取<b>较高者</b>。
     *
     * <p>走 {@code MinerEnchantments} 的自定义加成（现场骰 {@code 0..L}），不再用原版的
     * {@code ApplyBonusCount} / 战利品上下文；因此这份时运同样会按产出倍率多耗电。
     * 精准模式下没有时运。
     */
    @Override
    public int getFortuneLevel() {
        if (isSilkTouchActive()) return 0;
        return Math.max(getDropCountMultiplier(), super.getFortuneLevel());
    }

    /** 大型采矿机只给粉碎矿吃时运（其余掉落原样），与原后处理的行为一致。 */
    @Override
    protected boolean isFortuneTarget(ItemStack stack) {
        return ChemicalHelper.getPrefix(stack.getItem()) == TagPrefix.crushed;
    }

    /** 非精准模式时做后处理：把矿石方块按研磨机配方加工成粉碎矿。 */
    @Override
    protected boolean hasPostProcessing() {
        return !isSilkTouchActive();
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
