package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblockpro.ParamKey;
import com.gregtechceu.gtceu.api.machine.multiblockpro.PortKey;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.recipe.content.ContentRoll;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.api.recipe.handler.ActionResult;
import com.gregtechceu.gtceu.api.recipe.handler.PlanScratch;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.gto.datasynclib.AbstractDataSerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.annotations.SyncToClient;
import com.gto.datasynclib.datastream.codec.ValueOps;

import java.util.*;

public class AssemblyLineMachine extends WorkableElectricMultiblockMachine {

    public static final PortKey SECTION_IN = PortKey.IN;
    public static final PortKey SECTION_OUT = PortKey.OUT;
    public static final ParamKey SECTIONS = ParamKey.of("gtceu.multiblock.assembly_line.sections", "gtceu.multiblock.assembly_line.sections.desc");

    private List<KeyInventory<AEItemKey>> itemStackTransfers = new ArrayList<>();
    private List<KeyInventory<AEFluidKey>> fluidStackTransfers = new ArrayList<>();

    @SyncToClient
    private final RenderState renderState = new RenderState();

    public AssemblyLineMachine(MetaMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public void beforeWorking(RecipeHandlerUnit unit, GTRecipe recipe) {
        super.beforeWorking(unit, recipe);
        renderState.markAsChanged();
        requestSync();
    }

    @OnlyIn(Dist.CLIENT)
    public float getRendererLineCount(float partialTick) {
        if (renderState.duration <= 0) return 0;
        float progress = renderState.progress + Math.max(0, GTValues.CLIENT_TIME - renderState.syncTime) + partialTick;
        return Mth.clamp(progress / renderState.duration, 0, 1) * renderState.inputs;
    }

    /** A render-only snapshot sampled by the existing block entity sync, without a recipe tick hook. */
    private final class RenderState extends AbstractDataSerializable {

        private int progress;
        private int duration;
        private int inputs;
        private int syncTime;

        @Override
        public boolean detectChange() {
            return changed || recipeLogic.getMaxProgress() > 0 || duration > 0;
        }

        @Override
        public void writeBuffer(LogicalSide side, FriendlyByteBuf buffer) {
            progress = recipeLogic.getProgress();
            duration = recipeLogic.getMaxProgress();
            var recipe = recipeLogic.getLastRecipe();
            inputs = recipe == null ? 0 : Math.min(17, Math.max(recipe.itemInputs.size(), recipe.fluidInputs.size()));
            buffer.writeVarInt(progress);
            buffer.writeVarInt(duration);
            buffer.writeVarInt(inputs);
        }

        @Override
        public void readBuffer(LogicalSide side, FriendlyByteBuf buffer) {
            progress = buffer.readVarInt();
            duration = buffer.readVarInt();
            inputs = buffer.readVarInt();
            syncTime = GTValues.CLIENT_TIME;
        }

        @Override
        public Object writeValue(ValueOps ops) {
            var data = new ArrayList<Object>(3);
            ops.addInt(data, progress);
            ops.addInt(data, duration);
            ops.addInt(data, inputs);
            return ops.createList(data);
        }

        @Override
        public void readValue(Object data, ValueOps ops) {
            var list = ops.getList(data);
            progress = ops.getInt(list, 0);
            duration = ops.getInt(list, 1);
            inputs = ops.getInt(list, 2);
        }
    }

    @Override
    public boolean matchRecipe(RecipeHandlerUnit unit, GTRecipe recipe) {
        if (!super.matchRecipeOutput(recipe)) return false;
        if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineItems) {
            if (!consumeOrderedInputs(itemStackTransfers, recipe, recipe.itemInputs, true)) {
                setIdleReason(ActionResult.FAIL_ORDERED_ITEM);
                return false;
            }
        } else {
            if (!unit.matchInputs(only(recipe, true))) {
                setIdleReason(() -> ActionResult.failInsufficientIn(ItemRecipeInfo.INSTANCE.getName()).reason());
                return false;
            }
        }
        if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineFluids) {
            if (!consumeOrderedInputs(fluidStackTransfers, recipe, recipe.fluidInputs, true)) {
                setIdleReason(ActionResult.FAIL_ORDERED_FLUID);
                return false;
            }
            return true;
        } else {
            if (!unit.matchInputs(only(recipe, false))) {
                setIdleReason(() -> ActionResult.failInsufficientIn(FluidRecipeInfo.INSTANCE.getName()).reason());
                return false;
            }
            return true;
        }
    }

    @Override
    public Comparator<IMultiPart> getPartSorter() {
        return Comparator.comparing(p -> p.self().getPos(), RelativeDirection.RIGHT.getSorter(getFrontFacing(), getUpwardsFacing(), isFlipped()));
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        itemStackTransfers = new ArrayList<>();
        fluidStackTransfers = new ArrayList<>();
        for (Object part : getParts()) {
            if (part instanceof ItemBusPartMachine itemBusPart) {
                itemStackTransfers.add(itemBusPart.getInventory().storage);
            } else if (part instanceof FluidHatchPartMachine fluidHatch) {
                fluidStackTransfers.add(fluidHatch.tank.storage);
            }
        }
    }

    @Override
    public boolean handleRecipeInput(RecipeHandlerUnit unit, GTRecipe recipe) {
        boolean unitUsed = false;
        if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineItems) {
            if (!consumeOrderedInputs(itemStackTransfers, recipe, recipe.itemInputs, false)) {
                setIdleReason(ActionResult.FAIL_ORDERED_ITEM);
                return false;
            }
        } else {
            if (!consumeFromUnit(unit, only(recipe, true))) {
                setIdleReason(() -> ActionResult.failInsufficientIn(ItemRecipeInfo.INSTANCE.getName()).reason());
                return false;
            }
            unitUsed = true;
        }
        if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineFluids) {
            if (!consumeOrderedInputs(fluidStackTransfers, recipe, recipe.fluidInputs, false)) {
                setIdleReason(ActionResult.FAIL_ORDERED_FLUID);
                return false;
            }
        } else {
            if (!consumeFromUnit(unit, only(recipe, false))) {
                setIdleReason(() -> ActionResult.failInsufficientIn(FluidRecipeInfo.INSTANCE.getName()).reason());
                return false;
            }
            unitUsed = true;
        }
        if (unitUsed) unit.onCommitted(recipe);
        return true;
    }

    private static GTRecipe only(GTRecipe recipe, boolean items) {
        var r = recipe.copy();
        r.ocLevel = recipe.ocLevel;
        if (items) {
            r.fluidInputs = ContentList.EMPTY;
        } else {
            r.itemInputs = ContentList.EMPTY;
        }
        return r;
    }

    private static boolean consumeFromUnit(RecipeHandlerUnit unit, GTRecipe recipe) {
        if (recipe.itemInputs.isEmpty() && recipe.fluidInputs.isEmpty()) return true;
        var p = PlanScratch.acquire();
        try {
            unit.rollInputs(recipe, p);
            if (!unit.planInputs(recipe, p, recipe.scale, true)) return false;
            if (!unit.commitFallible(p)) return false;
            unit.commitArrays(p);
            return true;
        } finally {
            PlanScratch.release();
        }
    }

    @SuppressWarnings("unchecked")
    private static <K extends AEKey> boolean consumeOrderedInputs(List<KeyInventory<K>> machineInputs, GTRecipe recipe, ContentList list, boolean simulate) {
        int n = list.size();
        if (n == 0) return true;
        if (machineInputs.size() < n) return false;

        for (int i = 0; i < n; i++) {
            var inputSlot = machineInputs.get(i);
            long need = list.isConsumable(i) ? list.effective(i, recipe.scale) : list.amount(i);
            long stored = inputSlot.amountAt(0);
            if (stored < need || stored == 0 || !KeyIngredient.accepts(list.ingredient(i), inputSlot.uidAt(0), inputSlot.rawKeyAt(0))) {
                return false;
            }
            if (simulate) continue;
            long consume = ContentRoll.rolled(recipe, list, i, ContentRoll.RNG);
            if (consume > 0) inputSlot.extract(0, (K) inputSlot.rawKeyAt(0), consume, false);
        }
        return true;
    }
}
