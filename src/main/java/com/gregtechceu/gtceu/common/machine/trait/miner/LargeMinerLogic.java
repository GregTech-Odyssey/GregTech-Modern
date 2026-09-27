package com.gregtechceu.gtceu.common.machine.trait.miner;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.IItemRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.recipesearch.IntLongMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjLongConsumer;

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
     * <p>
     * 走 {@code MinerEnchantments} 的自定义加成（现场骰 {@code 0..L}），不再用原版的
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

    // ===== 后处理：把矿石方块按机器配方类型加工，用产物替换掉落 =====

    /** 有配方的方块状态 -> 配方。 */
    static final Reference2ReferenceOpenHashMap<BlockState, GTRecipe> recipes = new Reference2ReferenceOpenHashMap<>();
    /** 检索过、但没有配方的方块状态，避免每挖一块都白检索一次。 */
    static final ReferenceOpenHashSet<BlockState> noRecipe = new ReferenceOpenHashSet<>();

    /** 配方检索的输入：只有当前这一块方块。 */
    private final IItemRecipeHandler processingInput = new IItemRecipeHandler() {

        @Override
        public boolean forEachItems(ObjLongPredicate<ItemStack> function) {
            return !processingStack.isEmpty() && function.test(processingStack, processingStack.getCount());
        }

        @Override
        public void fastForEachItems(ObjLongConsumer<ItemStack> function) {
            if (!processingStack.isEmpty()) function.accept(processingStack, processingStack.getCount());
        }

        @Override
        public boolean handleRecipeItem(IO io, GTRecipe recipe, List<Content<ItemIngredient>> items, boolean simulate) {
            return false;
        }

        @Override
        public IntLongMap getSearchMap(GTRecipeType type) {
            processingSearchMap.clear();
            if (!processingStack.isEmpty()) {
                type.convertItem(processingStack, processingStack.getCount(), processingSearchMap);
            }
            return processingSearchMap;
        }
    };

    private ItemStack processingStack = ItemStack.EMPTY;
    private final IntLongMap processingSearchMap = new IntLongMap();
    private final List<ItemStack> processingOutputs = new ArrayList<>();

    @Override
    protected boolean doPostProcessing(List<ItemStack> blockDrops, BlockState blockState) {
        GTRecipe recipe = findPostProcessingRecipe(miner.getRecipeType(), blockState);
        if (recipe == null) return false;
        var outputs = processingOutputs;
        outputs.clear();
        // 产物按配方概率现场骰点，数量会变，所以每块都得复制
        for (var content : RecipeHelper.copyAndRoll(recipe, recipe.itemOutputs)) {
            var stack = content.inner.getInnerItemStack().copy();
            stack.setCount((int) Math.min(Integer.MAX_VALUE, content.amount));
            outputs.add(stack);
        }
        if (outputs.isEmpty()) return false;
        blockDrops.clear();
        dropPostProcessing(blockDrops, outputs, blockState);
        return true;
    }

    /**
     * 找出加工这个方块的配方。
     *
     * <p>
     * 检索结果只跟方块状态有关，所以缓存起来；电压等级在取用时再判，免得不同等级的采矿机互相串味。
     *
     * @return 没有配方，或者配方电压等级超出本机时返回 {@code null}
     */
    @Nullable
    private GTRecipe findPostProcessingRecipe(GTRecipeType recipeType, BlockState blockState) {
        var recipe = recipes.get(blockState);
        if (recipe == null) {
            if (noRecipe.contains(blockState)) return null;
            recipe = searchPostProcessingRecipe(recipeType, blockState);
            if (recipe == null) {
                noRecipe.add(blockState);
                return null;
            }
            recipes.put(blockState, recipe);
        }
        return GTUtil.getTierByVoltage(recipe.getInputEUt()) <= getVoltageTier() ? recipe : null;
    }

    /**
     * 把方块当输入在本机的配方类型里检索一条配方。
     *
     * @return 没检索到时返回 {@code null}
     */
    @Nullable
    private GTRecipe searchPostProcessingRecipe(GTRecipeType recipeType, BlockState blockState) {
        processingStack = new ItemStack(blockState.getBlock());
        try {
            var inputUnit = RecipeHandlerUnit.of(IO.IN, processingInput);
            var searchMap = inputUnit.getSearchMap(recipeType);
            if (searchMap.isEmpty()) return null;
            var found = new GTRecipe[1];
            if (!recipeType.search(inputUnit, searchMap, (unit, definition) -> {
                found[0] = definition.toRuntime();
                return true;
            })) {
                return null;
            }
            return found[0];
        } finally {
            processingStack = ItemStack.EMPTY;
        }
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
