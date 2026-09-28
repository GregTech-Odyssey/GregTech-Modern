package com.gregtechceu.gtceu.common.machine.trait.miner;

import com.gregtechceu.gtceu.api.capability.IMiner;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.common.data.GTMaterialItems;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.utils.BlockDropCache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

import com.gto.datasynclib.annotations.SaveToDisk;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class MinerLogic extends RecipeLogic {

    private static final short MAX_SPEED = Short.MAX_VALUE;
    private static final byte POWER = 5;
    private static final byte TICK_TOLERANCE = 20;
    private static final double DIVIDEND = MAX_SPEED * Math.pow(TICK_TOLERANCE, POWER);
    protected final IMiner miner;

    protected final int speed;
    @Getter
    protected final int maximumRadius;
    @Getter
    protected ItemStack pickaxeTool;
    /** 方块掉落缓存：一台矿机一个。 */
    protected final BlockDropCache dropCache;
    protected final LinkedList<BlockPos> blocksToMine = new LinkedList<>();
    protected final ArrayList<ItemStack> blockDrops = new ArrayList<>();
    @Getter
    @SaveToDisk
    protected int x = Integer.MAX_VALUE;
    @Getter
    @SaveToDisk
    protected int y = Integer.MAX_VALUE;
    @Getter
    @SaveToDisk
    protected int z = Integer.MAX_VALUE;
    @Getter
    @SaveToDisk
    protected int startX = Integer.MAX_VALUE;
    @Getter
    @SaveToDisk
    protected int startZ = Integer.MAX_VALUE;
    @Getter
    @SaveToDisk
    protected int startY = Integer.MAX_VALUE;
    @Getter
    @SaveToDisk
    protected int pipeY = Integer.MAX_VALUE;
    @Getter
    @SaveToDisk
    protected int mineX = Integer.MAX_VALUE;
    @Getter
    @SaveToDisk
    protected int mineZ = Integer.MAX_VALUE;
    @Getter
    @SaveToDisk
    protected int mineY = Integer.MAX_VALUE;
    @Getter
    protected int minBuildHeight = Integer.MAX_VALUE;
    @Getter
    protected int maxBuildHeight = Integer.MAX_VALUE;

    @Getter
    @Setter
    @SaveToDisk
    protected int currentRadius;
    @Getter
    @SaveToDisk
    protected boolean isDone;
    @Getter
    protected boolean isInventoryFull;
    @Getter
    @Setter
    protected Direction dir = Direction.DOWN;

    /** 速度倍率的参照方块：矿机挖的都是石质矿石，取石头作基准，保证倍率不随队列里的方块跳动。 */
    protected static final BlockState SPEED_REFERENCE = Blocks.STONE.defaultBlockState();

    /**
     * 本次 tick 实际生效的时运倍率（含被电力削弱后的部分），由 {@link #resolveEnchantmentCost} 写入。
     */
    @Getter
    protected double activeFortuneMultiplier = 1.0D;
    /** 本次 tick 实际生效的效率倍率（含被电力削弱后的部分）。 */
    @Getter
    protected double activeSpeedMultiplier = 1.0D;

    /**
     * Creates the general logic for all in-world ore block miners
     *
     * @param machine       the {@link MetaMachine} this logic belongs to
     * @param fortune       the fortune amount to apply when mining ores
     * @param speed         the speed in ticks per block mined
     * @param maximumRadius the maximum radius (square shaped) the miner can mine in
     */
    public MinerLogic(@NotNull IRecipeLogicMachine machine, int fortune, int speed, int maximumRadius) {
        super(machine);
        this.miner = (IMiner) machine;
        this.speed = speed;
        this.currentRadius = maximumRadius;
        this.maximumRadius = maximumRadius;
        this.isDone = false;
        this.pickaxeTool = GTMaterialItems.TOOL_ITEMS.get(GTMaterials.Neutronium, GTToolType.PICKAXE).get().get();
        this.dropCache = new BlockDropCache(BlockDropCache.LootParamsFactory.withTool(pickaxeTool));
        interval = 0;
    }

    //////////////////////////////////////
    // ********** 附魔槽 **********//
    //////////////////////////////////////

    /**
     * 附魔槽里的时运等级。
     *
     * <p>
     * 精准采集与时运互斥：只要这台矿机走精准掉落，时运一律按 0 处理。
     *
     * @return 时运等级，没有附魔槽 / 没有书时为 {@code 0}
     */
    public int getFortuneLevel() {
        return MinerEnchantments.getFortuneLevel(miner.getEnchantmentSlot(), isSilkTouchActive());
    }

    /** @return 附魔槽里的效率等级，没有时为 {@code 0} */
    public int getEfficiencyLevel() {
        return MinerEnchantments.getEfficiencyLevel(miner.getEnchantmentSlot());
    }

    /** @return 附魔槽里的耐久等级，没有时为 {@code 0} */
    public int getUnbreakingLevel() {
        return MinerEnchantments.getUnbreakingLevel(miner.getEnchantmentSlot());
    }

    /**
     * 是否走精准采集掉落。
     *
     * <p>
     * 机器自带的精准模式优先；机器没有精准模式时，看附魔槽里有没有精准采集书。
     *
     * @see #isSilkTouchMode()
     */
    public boolean isSilkTouchActive() {
        return MinerEnchantments.isSilkTouchActive(miner.getEnchantmentSlot(), isSilkTouchMode());
    }

    /**
     * 时运带来的产出倍率，取原版「每个掉落额外 +rand(0..L)」的期望值 {@code 1 + L/2}。
     *
     * <p>
     * 这里只用于计费；实际产出走 {@link #applyFortune} 现场骰点。
     */
    public double getFortuneMultiplier() {
        return MinerEnchantments.getFortuneMultiplier(getFortuneLevel());
    }

    /**
     * 效率带来的速度倍率，按原版「挖掘速度 += L² + 1」折算：{@code (基准 + L² + 1) / 基准}。
     *
     * <p>
     * 基准取矿机所用镐对参照方块的挖掘速度，见 {@link #SPEED_REFERENCE}。
     */
    public double getSpeedMultiplier() {
        return MinerEnchantments.getSpeedMultiplier(getEfficiencyLevel(), getBaseToolSpeed());
    }

    private double getBaseToolSpeed() {
        float toolSpeed = pickaxeTool.getDestroySpeed(SPEED_REFERENCE);
        return toolSpeed > 0 ? toolSpeed : 1.0D;
    }

    /**
     * 耐久带来的省电倍率。
     *
     * <p>
     * 按原版语义折算：耐久 L 有 {@code L/(L+1)} 的概率不掉耐久，等价于平均只消耗
     * {@code 1/(L+1)}，所以耗电乘以 {@code 1/(L+1)}——L=3 时省电到四分之一。
     *
     * @return 不大于 1 的倍率；没有耐久时为 {@code 1}
     */
    public double getPowerSavingMultiplier() {
        return MinerEnchantments.getPowerSavingMultiplier(getUnbreakingLevel());
    }

    /**
     * 按可用资源解析本次实际生效的附魔强度，并返回该扣多少代价。
     *
     * <p>
     * 结算顺序是「先省电、再按加成涨价」：耐久先把基础代价打成
     * {@code 基础 × 1/(L+1)}（见 {@link #getPowerSavingMultiplier()}），
     * 时运与效率再各自按其倍率把这份代价抬高。
     *
     * <p>
     * 资源不够时不会直接停机，而是求一个 {@code k ∈ [0,1]}，把时运与效率的加成等比缩成
     * {@code 1 + (倍率-1)·k}，使实际代价落在可用量内——也就是「电力不够自动削弱效果」。
     * 耐久的折扣属于「少花」而不是「多赚」，因此始终全额生效，不参与缩放。
     * 只有连打完折的基础代价都付不起时才由调用方判定停机。
     *
     * <p>
     * 结果写入 {@link #activeFortuneMultiplier} / {@link #activeSpeedMultiplier}，
     * 挖掘间隔与掉落都读这两个值，保证同一个 tick 内计费与效果一致。
     *
     * @param baseCost  不带附魔时的单 tick 代价
     * @param available 当前可用资源（EU 或蒸汽）
     * @return 实际应扣除的代价
     */
    public long resolveEnchantmentCost(long baseCost, long available) {
        var resolved = MinerEnchantments.resolveCost(baseCost, available, getFortuneMultiplier(),
                getSpeedMultiplier(), getPowerSavingMultiplier());
        activeFortuneMultiplier = resolved.fortuneMultiplier();
        activeSpeedMultiplier = resolved.speedMultiplier();
        return resolved.cost();
    }

    /**
     * 本次实际生效的挖掘间隔（tick / 方块），已计入效率与电力削弱。
     *
     * @return 至少为 1
     */
    public int getActiveSpeed() {
        return MinerEnchantments.getActiveSpeed(speed, activeSpeedMultiplier);
    }

    /**
     * 按原版时运语义给掉落加成：每堆额外增加 {@code rand(0..L)} 个。
     *
     * <p>
     * 不使用原版的 {@code ApplyBonusCount} / 战利品上下文，而是直接掷点，
     * 因此对任何掉落物都生效，不依赖方块是否走战利品表。
     */
    private void applyFortune(List<ItemStack> blockDrops, ServerLevel level) {
        MinerEnchantments.applyFortune(blockDrops, getFortuneLevel(), level.getRandom());
    }

    @Override
    public void resetRecipeLogic() {
        super.resetRecipeLogic();
        resetArea(false);
    }

    private static void setBlock(Level level, BlockPos pos) {
        level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 3);
    }

    /**
     * Performs the actual mining in world
     * Call this method every tick in update
     */
    @Override
    public void serverTick() {
        if (!isSuspend() && getMachine().getLevel() instanceof ServerLevel serverLevel && checkCanMine()) {
            // if the inventory is not full, drain energy etc. from the miner
            // the storages have already been checked earlier
            if (!isInventoryFull) {
                // always drain storages when working, even if blocksToMine ends up being empty
                miner.drainInput(false);
                // since energy is being consumed the miner is now active
                setStatus(WORKING);
            } else {
                // the miner cannot drain, therefore it is inactive
                if (this.isWorking()) {
                    setWaiting(Component.translatable("gtceu.recipe_logic.insufficient_out").append(": ").append(ItemRecipeInfo.INSTANCE.getName()));
                }
            }
            // drill a hole beneath the miner and extend the pipe downwards by one
            if ((dir == Direction.DOWN && mineY < pipeY) || (dir == Direction.UP && mineY > pipeY)) {
                var miningPos = getMiningPos();
                var pipePos = new BlockPos(miningPos.getX(), pipeY, miningPos.getZ());
                if (serverLevel.getBlockState(pipePos).getDestroySpeed(serverLevel, pipePos) < 0) {
                    isDone = true;
                    setStatus(IDLE);
                    return;
                }
                serverLevel.destroyBlock(pipePos, false);
                if (dir == Direction.UP) {
                    ++pipeY;
                } else {
                    --pipeY;
                }
            }
            // check if the miner needs new blocks to mine and get them if needed
            checkBlocksToMine();
            // if there are blocks to mine and the correct amount of time has passed, do the mining
            if (getOffsetTimer() % getActiveSpeed() == 0 && !blocksToMine.isEmpty()) {
                var blockDrops = this.blockDrops;
                blockDrops.clear();
                BlockState blockState = serverLevel.getBlockState(blocksToMine.getFirst());
                // check to make sure the ore is still there,
                while (!blockState.is(Tags.Blocks.ORES)) {
                    blocksToMine.removeFirst();
                    if (blocksToMine.isEmpty()) break;
                    blockState = serverLevel.getBlockState(blocksToMine.getFirst());
                }
                // When we are here we have an ore to mine! I'm glad we aren't threaded
                if (!blocksToMine.isEmpty() & blockState.is(Tags.Blocks.ORES)) {
                    // get the block's drops.
                    if (isSilkTouchActive()) {
                        getSilkTouchDrops(blockDrops, blockState);
                    } else {
                        // 后处理：有配方类型的话，用配方产物替换普通掉落（大型采矿机的粉碎矿）
                        if (hasPostProcessing()) {
                            doPostProcessing(blockDrops, blockState);
                        } else {
                            getRegularBlockDrops(blockDrops, blockState, blocksToMine.getFirst());
                        }
                        // 时运：按原版骰点额外加产出，不使用原版的战利品函数
                        applyFortune(blockDrops, serverLevel);
                    }
                    // try to insert them
                    mineAndInsertItems(blockDrops, serverLevel);
                }
            }
            if (blocksToMine.isEmpty()) {
                // there were no blocks to mine, so the current position is the previous position
                x = mineX;
                y = mineY;
                z = mineZ;
                // attempt to get more blocks to mine, if there are none, the miner is done mining
                blocksToMine.addAll(getBlocksToMine());
                if (blocksToMine.isEmpty()) {
                    this.isDone = true;
                    this.setStatus(IDLE);
                }
            }
        } else {
            // machine isn't working enabled
            this.setStatus(IDLE);
            unsubscribe();
        }
    }

    /**
     * @return true if the miner is able to mine, else false
     */
    protected boolean checkCanMine() {
        // if the miner is finished, the target coordinates are invalid, or it cannot drain storages, stop
        // if the miner is not finished and has invalid coordinates, get new and valid starting coordinates
        if (!isDone && checkCoordinatesInvalid()) {
            initPos(getMiningPos(), currentRadius);
        }
        return !isDone && miner.drainInput(true);
    }

    /**
     * Called after each block is mined, used to perform additional actions afterwards
     */
    protected void onMineOperation() {}

    protected boolean isSilkTouchMode() {
        return false;
    }

    /**
     * called to handle mining regular ores and blocks
     *
     * @param blockDrops the List of items to fill after the operation
     * @param blockState the {@link BlockState} of the block being mined
     */
    protected void getRegularBlockDrops(List<ItemStack> blockDrops, BlockState blockState, BlockPos blockPos) {
        // 掉落直接取缓存本体，输出仓不会改写它，所以不复制
        dropCache.forEach((ServerLevel) getMachine().getLevel(), blockState, blockPos, blockDrops::add);
    }

    protected int getVoltageTier() {
        return 0;
    }

    /**
     * 是否对采到的方块做后处理（按机器配方类型加工）。默认不做。
     *
     * <p>
     * 目前只有大型采矿机需要，实现放在 {@code LargeMinerLogic}，其它矿机不必白挂一套处理器。
     *
     * @see #doPostProcessing
     */
    protected boolean hasPostProcessing() {
        return false;
    }

    /** 后处理：用配方产物替换掉落。默认不做。 */
    protected boolean doPostProcessing(List<ItemStack> blockDrops, BlockState blockState) {
        return false;
    }

    /**
     * called to handle mining regular ores and blocks with silk touch
     *
     * @param blockDrops the List of items to fill after the operation
     * @param blockState the {@link BlockState} of the block being mined
     */
    protected static void getSilkTouchDrops(List<ItemStack> blockDrops, BlockState blockState) {
        blockDrops.add(new ItemStack(blockState.getBlock()));
    }

    /**
     * called in order to insert the mined items into the inventory and actually remove the block in world
     * marks the inventory as full if the items cannot fit, and not full if it previously was full and items could fit
     *
     * @param blockDrops the List of items to insert
     * @param world      the {@link ServerLevel} the miner is in
     */
    private void mineAndInsertItems(List<ItemStack> blockDrops, ServerLevel world) {
        // If the block's drops can fit in the inventory, move the previously mined position to the block
        // replace the ore block with cobblestone instead of breaking it to prevent mob spawning
        // remove the ore block's position from the mining queue
        if (machine.outputItem(blockDrops.toArray(new ItemStack[0]))) {
            var pos = blocksToMine.getFirst();
            setBlock(world, pos);
            mineX = pos.getX();
            mineZ = pos.getZ();
            mineY = pos.getY();
            blocksToMine.removeFirst();
            onMineOperation();
            // if the inventory was previously considered full, mark it as not since an item was able to fit
            isInventoryFull = false;
        } else {
            // the ore block was not able to fit, so the inventory is considered full
            isInventoryFull = true;
        }
    }

    /**
     * This method designates the starting position for mining blocks
     *
     * @param pos           the {@link BlockPos} of the miner itself
     * @param currentRadius the currently set mining radius
     */
    public void initPos(@NotNull BlockPos pos, int currentRadius) {
        x = pos.getX() - currentRadius;
        z = pos.getZ() - currentRadius;
        if (dir == Direction.UP) {
            y = pos.getY() + 1;
        } else {
            y = pos.getY() - 1;
        }
        startX = pos.getX() - currentRadius;
        startZ = pos.getZ() - currentRadius;
        startY = pos.getY();
        if (dir == Direction.UP) {
            pipeY = pos.getY() + 1;
        } else {
            pipeY = pos.getY() - 1;
        }
        mineX = pos.getX() - currentRadius;
        mineZ = pos.getZ() - currentRadius;
        if (dir == Direction.UP) {
            mineY = pos.getY() + 1;
        } else {
            mineY = pos.getY() - 1;
        }
        onRemove();
    }

    /**
     * Checks if the current coordinates are invalid
     *
     * @return {@code true} if the coordinates are invalid, else false
     */
    private boolean checkCoordinatesInvalid() {
        return x == Integer.MAX_VALUE && y == Integer.MAX_VALUE && z == Integer.MAX_VALUE;
    }

    /**
     * Checks whether there are any more blocks to mine, if there are currently none queued
     */
    public void checkBlocksToMine() {
        if (blocksToMine.isEmpty()) blocksToMine.addAll(getBlocksToMine());
    }

    /**
     * Recalculates the mining area, refills the block list and restarts the miner, if it was done
     */
    public void resetArea(boolean checkToMine) {
        initPos(getMiningPos(), currentRadius);
        if (this.isDone) this.setWorkingEnabled(false);
        this.isDone = false;
        if (checkToMine) {
            blocksToMine.clear();
            checkBlocksToMine();
        }
    }

    /**
     * Gets the blocks to mine
     *
     * @return a {@link LinkedList} of {@link BlockPos} for each ore to mine
     */
    private LinkedList<BlockPos> getBlocksToMine() {
        LinkedList<BlockPos> blocks = new LinkedList<>();
        // determine how many blocks to retrieve this time
        var level = getMachine().getLevel();
        assert level != null;
        double quotient = getQuotient(getMeanTickTime(level));
        int calcAmount = quotient < 1 ? 1 : (int) (Math.min(quotient, Short.MAX_VALUE));
        int calculated = 0;
        if (this.minBuildHeight == Integer.MAX_VALUE) this.minBuildHeight = level.getMinBuildHeight();
        if (this.maxBuildHeight == Integer.MAX_VALUE) this.maxBuildHeight = level.getMaxBuildHeight();
        // keep getting blocks until the target amount is reached
        while (calculated < calcAmount) {
            // moving down the y-axis
            if (y > minBuildHeight && y < maxBuildHeight) {
                // moving across the z-axis
                if (z <= startZ + currentRadius * 2) {
                    // check every block along the x-axis
                    if (x <= startX + currentRadius * 2) {
                        BlockPos blockPos = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(blockPos);
                        if (state.getDestroySpeed(level, blockPos) >= 0 && level.getBlockEntity(blockPos) == null && state.is(Tags.Blocks.ORES)) {
                            blocks.addLast(blockPos);
                        }
                        // move to the next x position
                        ++x;
                    } else {
                        // reset x and move to the next z layer
                        x = startX;
                        ++z;
                    }
                } else {
                    // reset z and move to the next y layer
                    z = startZ;
                    if (dir == Direction.UP) {
                        ++y;
                    } else {
                        --y;
                    }
                }
            } else return blocks;
            // only count iterations where blocks were found
            if (!blocks.isEmpty()) calculated++;
        }
        return blocks;
    }

    /**
     * @param values to find the mean of
     * @return the mean value
     */
    private static long mean(long[] values) {
        if (values.length == 0L) return 0L;
        long sum = 0L;
        for (long v : values) sum += v;
        return sum / values.length;
    }

    /**
     * @param world the {@link Level} to get the average tick time of
     * @return the mean tick time
     */
    private static double getMeanTickTime(@NotNull Level world) {
        return mean(Objects.requireNonNull(world.getServer()).tickTimes) * 1.0E-6;
    }

    /**
     * gets the quotient for determining the amount of blocks to mine
     *
     * @param base is a value used for calculation, intended to be the mean tick time of the world the miner is in
     * @return the quotient
     */
    private static double getQuotient(double base) {
        return DIVIDEND / Math.pow(base, POWER);
    }

    /**
     * @return the position to start mining from
     */
    public BlockPos getMiningPos() {
        return getMachine().getPos();
    }

    public void onRemove() {}
}
