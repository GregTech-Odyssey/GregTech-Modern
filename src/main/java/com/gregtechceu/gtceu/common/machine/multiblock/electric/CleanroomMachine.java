package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.SimpleGeneratorMachine;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMufflerMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblockpro.ParamKey;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Piece;
import com.gregtechceu.gtceu.api.machine.multiblockpro.PortKey;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Size;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Slot;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Structure;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Symbols;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.pattern.*;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.item.PortableScannerBehavior;
import com.gregtechceu.gtceu.common.machine.electric.HullMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DiodePartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.CokeOvenMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitiveBlastFurnaceMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitivePumpMachine;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomLogic;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.datastream.DataComponentKey;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.gregtechceu.gtceu.api.pattern.Predicates.abilities;
import static com.gregtechceu.gtceu.api.pattern.Predicates.blocks;
import static com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.*;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CleanroomMachine extends WorkableElectricMultiblockMachine implements ICleanroomProvider, IDataInfoProvider {

    public static final DataComponentKey<ReferenceSet<ICleanroomReceiver>> CLEANROOM_RECEIVER = DataComponentKey.create("cleanroomReceiver", DataComponentKey.collectionBuilder(ReferenceOpenHashSet::new));

    private static final TraceabilityPredicate INNER_PREDICATE = new TraceabilityPredicate(blockWorldState -> {
        if (blockWorldState.getTileEntity() instanceof MetaMachineBlockEntity machineBlockEntity) {
            var machine = machineBlockEntity.getMetaMachine();
            if (isMachineBanned(machine)) {
                blockWorldState.setError(MultiblockState.BANNED_ERROR.copy());
                return false;
            }
            if (machine instanceof ICleanroomReceiver cleanroomReceiver) {
                blockWorldState.getMatchContext().getOrCreate(CLEANROOM_RECEIVER, ReferenceOpenHashSet::new).add(cleanroomReceiver);
            }
        }
        return true;
    }, null, null) {

        @Override
        public boolean testOnly() {
            return true;
        }

        @Override
        public boolean isAny() {
            return false;
        }

        @Override
        public boolean isAir() {
            return false;
        }
    };

    public static final int CLEAN_AMOUNT_THRESHOLD = 95;
    public static final int MIN_CLEAN_AMOUNT = 0;
    public static final int MIN_RADIUS = 2;
    public static final int MIN_DEPTH = 4;
    public static final int MAX_RADIUS = 7;
    public static final int MAX_DEPTH = 14;
    public static final ParamKey LEFT_DIST = ParamKey.of("gtceu.multiblock.cleanroom.left", "gtceu.multiblock.cleanroom.left.desc");
    public static final ParamKey RIGHT_DIST = ParamKey.of("gtceu.multiblock.cleanroom.right", "gtceu.multiblock.cleanroom.right.desc");
    public static final ParamKey FRONT_DIST = ParamKey.of("gtceu.multiblock.cleanroom.front", "gtceu.multiblock.cleanroom.front.desc");
    public static final ParamKey BACK_DIST = ParamKey.of("gtceu.multiblock.cleanroom.back", "gtceu.multiblock.cleanroom.back.desc");
    public static final ParamKey RINGS = ParamKey.of("gtceu.multiblock.cleanroom.rings", "gtceu.multiblock.cleanroom.rings.desc");
    @SaveToDisk
    private int lDist = 0;
    @SaveToDisk
    private int rDist = 0;
    @SaveToDisk
    private int bDist = 0;
    @SaveToDisk
    private int fDist = 0;
    @SaveToDisk
    private int hDist = 0;
    @Nullable
    private CleanroomType cleanroomType = null;
    @SaveToDisk
    private int cleanAmount;
    // runtime
    @Nullable
    private EnergyContainerList inputEnergyContainers;
    @Nullable
    private Collection<ICleanroomReceiver> cleanroomReceivers;

    public CleanroomMachine(MetaMachineBlockEntity metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Override
    public boolean hasBatchConfig() {
        return false;
    }

    public RecipeLogic createRecipeLogic(Object... args) {
        return new CleanroomLogic(this);
    }

    @Override
    public CleanroomLogic getRecipeLogic() {
        return (CleanroomLogic) super.getRecipeLogic();
    }

    @Override
    public boolean supportLockRecipe() {
        return false;
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////
    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        updateStructureDimensions();
        initializeAbilities();
        var filterType = getMultiblockState().getMatchContext().get(Predicates.DataKey.FILTER_TYPE);
        if (filterType != null) {
            this.cleanroomType = filterType.getCleanroomType();
        } else {
            this.cleanroomType = CleanroomType.CLEANROOM;
        }
        // bind cleanroom
        if (cleanroomReceivers != null) {
            this.cleanroomReceivers.forEach(receiver -> receiver.setCleanroom(null));
            this.cleanroomReceivers = null;
        }
        this.cleanroomReceivers = getMultiblockState().getMatchContext().getOrDefault(CLEANROOM_RECEIVER, ReferenceSets.emptySet());
        this.cleanroomReceivers.forEach(receiver -> receiver.setCleanroom(this));
        // max progress is based roughly on the dimensions of the structure: ((w * d) ^ .8 * h)
        // taller cleanrooms take longer than wider ones
        // minimum of 100 is a 5x5x5 cleanroom: 125-25=100 ticks
        // max sized CR is around 1142 ticks per progression
        var area = (lDist + rDist + 1) * (bDist + fDist + 1);
        var duration = Math.pow(area, 0.8) * (hDist + 1);
        this.getRecipeLogic().setDuration(Math.max(100, (int) duration));
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.inputEnergyContainers = null;
        this.cleanAmount = MIN_CLEAN_AMOUNT;
        if (cleanroomReceivers != null) {
            this.cleanroomReceivers.forEach(receiver -> receiver.setCleanroom(null));
            this.cleanroomReceivers = null;
        }
    }

    @Override
    public boolean shouldAddPartToController(IMultiPart part) {
        var state = getMultiblockState();
        for (Direction side : GTUtil.DIRECTIONS) {
            if (!state.inStructure(part.self().getPos().relative(side).asLong())) {
                return true;
            }
        }
        return false;
    }

    protected void initializeAbilities() {
        List<IEnergyContainer> energyContainers = new ArrayList<>();
        for (var part : getWorkableParts()) {
            if (isPartIgnored(part)) continue;
            for (var handlerList : part.getRecipeHandlers()) {
                energyContainers.addAll(handlerList.getCapabilities(GTCapability.ENERGY_CONTAINER));
            }
            if (part instanceof IMaintenanceMachine maintenanceMachine) {
                getRecipeLogic().setMaintenanceMachine(maintenanceMachine);
            }
        }
        this.inputEnergyContainers = new EnergyContainerList(energyContainers);
        getRecipeLogic().setEnergyContainer(this.inputEnergyContainers);
        this.tier = Math.min(GTValues.MAX, GTUtil.getFloorTierByVoltage(getMaxVoltage()));
    }

    // `return false` being a separate statement is better for readability
    private static boolean isPartIgnored(IMultiPart part) {
        return switch (part) {
            case DiodePartMachine ignored -> true;
            case HullMachine ignored -> true;
            default -> false;
        };
    }

    public void updateStructureDimensions() {
        var assembly = getAssembly();
        if (assembly == null || !assembly.has(LEFT_DIST)) return;
        this.lDist = assembly.get(LEFT_DIST);
        this.rDist = assembly.get(RIGHT_DIST);
        this.fDist = assembly.get(FRONT_DIST);
        this.bDist = assembly.get(BACK_DIST);
        this.hDist = assembly.get(RINGS) + 1;
    }

    @Override
    public int checkPriority() {
        return 5;
    }

    @Override
    public boolean requiresServerCheck() {
        return true;
    }

    public static Structure structure(MultiblockMachineDefinition definition) {
        var wall = blocks(GTBlocks.PLASTCRETE.get(), GTBlocks.CLEANROOM_GLASS.get());
        var passthrough = abilities(PartAbility.PASSTHROUGH_HATCH);
        var base = abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(2)
                .or(blocks(GTMachines.MAINTENANCE_HATCH.get(), GTMachines.AUTO_MAINTENANCE_HATCH.get())
                        .setMinGlobalLimited(ConfigHolder.INSTANCE.machines.enableMaintenance ? 1 : 0).setMaxGlobalLimited(1))
                .or(passthrough);
        var symbols = Symbols.create()
                .where('C', Predicates.controller(definition))
                .where('F', Predicates.cleanroomFilters())
                .where('D', blocks(GTBlocks.PLASTCRETE.get()))
                .where(' ', INNER_PREDICATE)
                .where('W', wall.or(base).or(doorPredicate().setMaxGlobalLimited(8)))
                .where('E', wall.or(base).or(Predicates.blockTag(CustomTags.CLEANROOM_FLOORS).setMaxGlobalLimited(4)))
                .where('K', wall.or(Predicates.blockTag(CustomTags.CLEANROOM_FLOORS)))
                .wherePart('A', wall.or(base));
        var ceiling = Piece.sized(size -> Piece.start(LEFT, FRONT, DOWN)
                .aisle(layer(size, 'D', 'F', 'C'))
                .portAfter(PortKey.OUT)
                .build());
        var ring = Piece.sized(size -> Piece.start(LEFT, FRONT, DOWN)
                .aisle(layer(size, 'W', ' ', ' '))
                .portBefore(PortKey.IN)
                .portAfter(PortKey.OUT)
                .build());
        var floor = Piece.sized(size -> Piece.start(LEFT, FRONT, DOWN)
                .aisle(layer(size, 'A', 'E', 'K'))
                .portBefore(PortKey.IN)
                .build());
        return Structure.root(ceiling)
                .symbols(symbols)
                .measure(m -> m.param(LEFT_DIST).toward(LEFT).until('D').range(MIN_RADIUS, MAX_RADIUS))
                .measure(m -> m.param(RIGHT_DIST).toward(RIGHT).until('D').range(MIN_RADIUS, MAX_RADIUS))
                .measure(m -> m.param(FRONT_DIST).toward(FRONT).until('D').range(MIN_RADIUS, MAX_RADIUS))
                .measure(m -> m.param(BACK_DIST).toward(BACK).until('D').range(MIN_RADIUS, MAX_RADIUS))
                .require(size -> Math.abs(size.get(LEFT_DIST) - size.get(RIGHT_DIST)) <= 1 && Math.abs(size.get(FRONT_DIST) - size.get(BACK_DIST)) <= 1)
                .limit(passthrough, size -> width(size) * depth(size) / 4)
                .atPort(PortKey.OUT, Slot.chain(ring, PortKey.IN, PortKey.OUT, PortKey.IN)
                        .count(RINGS, MIN_DEPTH - 1, MAX_DEPTH - 1)
                        .backtrack()
                        .atPort(PortKey.OUT, Slot.one(floor, PortKey.IN)))
                .build();
    }

    private static int width(Size size) {
        return size.get(LEFT_DIST) + size.get(RIGHT_DIST) + 1;
    }

    private static int depth(Size size) {
        return size.get(FRONT_DIST) + size.get(BACK_DIST) + 1;
    }

    private static String[] layer(Size size, char edge, char inner, char center) {
        int width = width(size);
        int depth = depth(size);
        int centerChar = size.get(RIGHT_DIST);
        int centerRow = size.get(BACK_DIST);
        var rows = new String[depth];
        for (int j = 0; j < depth; j++) {
            var row = new StringBuilder(width);
            for (int i = 0; i < width; i++) {
                if (i == 0 || j == 0 || i == width - 1 || j == depth - 1) row.append(edge);
                else if (i == centerChar && j == centerRow) row.append(center);
                else row.append(inner);
            }
            rows[j] = row.toString();
        }
        return rows;
    }

    protected static TraceabilityPredicate doorPredicate() {
        return Predicates.custom(blockWorldState -> blockWorldState.getBlockState().is(CustomTags.CLEANROOM_DOORS), () -> BlockInfo.fromBlockState(Blocks.IRON_DOOR.defaultBlockState()), () -> new Block[] { Blocks.IRON_DOOR });
    }

    private static boolean isMachineBanned(MetaMachine machine) {
        // blacklisted machines: mufflers and all generators, miners/drills, primitives
        return switch (machine) {
            case ICleanroomProvider ignored -> true;
            case IMufflerMachine ignored -> true;
            case SimpleGeneratorMachine ignored -> true;
            case LargeMinerMachine ignored -> true;
            case FluidDrillMachine ignored -> true;
            case BedrockOreMinerMachine ignored -> true;
            case CokeOvenMachine ignored -> true;
            case PrimitiveBlastFurnaceMachine ignored -> true;
            case PrimitivePumpMachine ignored -> true;
            default -> false;
        };
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        if (isFormed()) {
            var maxVoltage = getMaxVoltage();
            if (maxVoltage > 0) {
                String voltageName = GTValues.VNF[GTUtil.getFloorTierByVoltage(maxVoltage)];
                textList.add(Component.translatable("gtceu.multiblock.max_energy_per_tick", maxVoltage, voltageName));
            }
            if (cleanroomType != null) {
                textList.add(Component.translatable(cleanroomType.getTranslationKey()));
            }
            if (!isWorkingEnabled()) {
                textList.add(Component.translatable("gtceu.multiblock.work_paused"));
            } else if (isActive()) {
                textList.add(Component.translatable("gtceu.multiblock.running"));
                int currentProgress = (int) (recipeLogic.getProgressPercent() * 100);
                double maxInSec = (float) recipeLogic.getDuration() / 20.0F;
                double currentInSec = (float) recipeLogic.getProgress() / 20.0F;
                textList.add(Component.translatable("gtceu.multiblock.progress", String.format("%.2f", (float) currentInSec), String.format("%.2f", (float) maxInSec), currentProgress));
            } else {
                textList.add(Component.translatable("gtceu.multiblock.idling"));
            }
            if (recipeLogic.isWaiting()) {
                textList.add(Component.translatable("gtceu.multiblock.waiting").setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
            }
            if (isClean()) textList.add(Component.translatable("gtceu.multiblock.cleanroom.clean_state"));
            else textList.add(Component.translatable("gtceu.multiblock.cleanroom.dirty_state"));
            textList.add(Component.translatable("gtceu.multiblock.cleanroom.clean_amount", this.cleanAmount));
            textList.add(Component.translatable("gtceu.multiblock.dimensions.0"));
            textList.add(Component.translatable("gtceu.multiblock.dimensions.1", lDist + rDist + 1, hDist + 1, fDist + bDist + 1));
        } else {
            Component tooltip = Component.translatable("gtceu.multiblock.invalid_structure.tooltip").withStyle(ChatFormatting.GRAY);
            textList.add(Component.translatable("gtceu.multiblock.invalid_structure").withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip))));
        }
    }

    @Override
    public Set<CleanroomType> getTypes() {
        return this.cleanroomType == null ? Collections.emptySet() : Set.of(this.cleanroomType);
    }

    /**
     * Adjust the cleanroom's clean amount
     *
     * @param amount the amount of cleanliness to increase/decrease by
     */
    public void adjustCleanAmount(int amount) {
        // do not allow negative cleanliness nor cleanliness above 100
        this.cleanAmount = Mth.clamp(this.cleanAmount + amount, 0, 100);
    }

    @Override
    public boolean isClean() {
        return this.cleanAmount >= CLEAN_AMOUNT_THRESHOLD;
    }

    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL || mode == PortableScannerBehavior.DisplayMode.SHOW_MACHINE_INFO) {
            return Collections.singletonList(Component.translatable(isClean() ? "gtceu.multiblock.cleanroom.clean_state" : "gtceu.multiblock.cleanroom.dirty_state"));
        }
        return new ArrayList<>();
    }

    @Override
    public long getMaxVoltage() {
        if (inputEnergyContainers == null) return GTValues.LV;
        return inputEnergyContainers.getInputVoltage();
    }

    // Do not allow cleanroom to be paused due to custom recipe logic
    @Override
    public boolean isWorkingEnabled() {
        return true;
    }

    @Override
    public void setWorkingEnabled(boolean ignored) {}
}
