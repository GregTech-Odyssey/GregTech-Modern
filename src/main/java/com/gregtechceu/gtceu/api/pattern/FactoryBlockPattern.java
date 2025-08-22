package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Array;
import java.util.List;
import java.util.function.Predicate;

public class FactoryBlockPattern {

    private final MultiblockMachineDefinition definition;
    private final List<String[]> depth;
    private final List<int[]> aisleRepetitions;
    private final Char2ObjectOpenHashMap<TraceabilityPredicate> symbolMap;
    private final RelativeDirection[] structureDir;
    private PatternCondition condition;
    private int aisleHeight;
    private int rowWidth;

    private FactoryBlockPattern(RelativeDirection charDir, RelativeDirection stringDir, RelativeDirection aisleDir, MultiblockMachineDefinition definition) {
        this.definition = definition;
        depth = new ObjectArrayList<>();
        aisleRepetitions = new ObjectArrayList<>();
        symbolMap = new Char2ObjectOpenHashMap<>();
        structureDir = new RelativeDirection[3];
        structureDir[0] = charDir;
        structureDir[1] = stringDir;
        structureDir[2] = aisleDir;
        int flags = 0;
        for (int i = 0; i < 3; i++) {
            switch (structureDir[i]) {
                case UP, DOWN -> flags |= 0x1;
                case LEFT, RIGHT -> flags |= 0x2;
                case FRONT, BACK -> flags |= 0x4;
            }
        }
        if (flags != 0x7) throw new IllegalArgumentException("Must have 3 different axes!");
    }

    /**
     * Adds a repeatable aisle to this pattern.
     */
    public FactoryBlockPattern aisleRepeatable(int minRepeat, int maxRepeat, String... aisle) {
        if (!ArrayUtils.isEmpty(aisle) && !StringUtils.isEmpty(aisle[0])) {
            if (this.depth.isEmpty()) {
                this.aisleHeight = aisle.length;
                this.rowWidth = aisle[0].length();
            }

            if (aisle.length != this.aisleHeight) {
                throw new IllegalArgumentException("Expected aisle with height of " + this.aisleHeight +
                        ", but was given one with a height of " + aisle.length + ")");
            } else {
                this.depth.add(aisle);
                if (minRepeat > maxRepeat)
                    throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
                aisleRepetitions.add(new int[] { minRepeat, maxRepeat });
                return this;
            }
        } else {
            throw new IllegalArgumentException("Empty pattern for aisle");
        }
    }

    /**
     * Adds a single aisle to this pattern. (so multiple calls to this will increase the aisleDir by 1)
     */
    public FactoryBlockPattern aisle(String... aisle) {
        return aisleRepeatable(1, 1, aisle);
    }

    /**
     * Set last aisle repeatable
     */
    public FactoryBlockPattern setRepeatable(int minRepeat, int maxRepeat) {
        if (minRepeat > maxRepeat)
            throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
        aisleRepetitions.set(aisleRepetitions.size() - 1, new int[] { minRepeat, maxRepeat });
        return this;
    }

    /**
     * Set last aisle repeatable
     */
    public FactoryBlockPattern setRepeatable(int repeatCount) {
        return setRepeatable(repeatCount, repeatCount);
    }

    public static FactoryBlockPattern start() {
        return new FactoryBlockPattern(RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT, null);
    }

    public static FactoryBlockPattern start(MultiblockMachineDefinition definition) {
        return new FactoryBlockPattern(RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT, definition);
    }

    public static FactoryBlockPattern start(RelativeDirection charDir, RelativeDirection stringDir,
                                            RelativeDirection aisleDir) {
        return new FactoryBlockPattern(charDir, stringDir, aisleDir, null);
    }

    public static FactoryBlockPattern start(MultiblockMachineDefinition definition, RelativeDirection charDir, RelativeDirection stringDir,
                                            RelativeDirection aisleDir) {
        return new FactoryBlockPattern(charDir, stringDir, aisleDir, definition);
    }

    public FactoryBlockPattern where(char symbol, TraceabilityPredicate blockMatcher) {
        if (blockMatcher.isAny()) return this;
        if (blockMatcher.isAir()) {
            this.symbolMap.put(symbol, TraceabilityPredicate.AIR);
        } else if (!blockMatcher.isController && blockMatcher.getClass() == TraceabilityPredicate.class) {
            this.symbolMap.put(symbol, new TraceabilityPredicate(blockMatcher).sort());
        } else {
            this.symbolMap.put(symbol, blockMatcher);
        }
        return this;
    }

    public FactoryBlockPattern condition(Predicate<MultiblockState> condition) {
        return condition(condition, "gtceu.recipe_logic.condition_fails");
    }

    public FactoryBlockPattern condition(Predicate<MultiblockState> condition, String translateKey) {
        this.condition = new PatternCondition(condition, translateKey);
        return this;
    }

    public BlockPattern build() {
        int size = this.depth.size();
        int[] centerOffset = new int[5];
        int[][] aisleRepetitions = this.aisleRepetitions.toArray(new int[this.aisleRepetitions.size()][]);
        TraceabilityPredicate[][][] predicate = (TraceabilityPredicate[][][]) Array.newInstance(TraceabilityPredicate.class, size, this.aisleHeight, this.rowWidth);

        for (int i = 0, minZ = 0, maxZ = 0; i < size; minZ += aisleRepetitions[i][0], maxZ += aisleRepetitions[i][1], i++) {
            for (int j = 0; j < this.aisleHeight; j++) {
                for (int k = 0; k < this.rowWidth; k++) {
                    var tp = this.symbolMap.get(this.depth.get(i)[j].charAt(k));
                    predicate[i][j][k] = tp;
                    if (tp != null && tp.isController) {
                        centerOffset = new int[] { k, j, i, minZ, maxZ };
                    }
                }
            }
        }

        var pattern = new BlockPattern(predicate, structureDir, aisleRepetitions, centerOffset);
        if (condition != null) pattern.condition = condition;
        if (definition != null) {
            pattern.predicates = symbolMap.values();
            definition.setCheckPriority(-(pattern.fingerLength * pattern.thumbLength * pattern.palmLength));
        }
        return pattern;
    }
}
