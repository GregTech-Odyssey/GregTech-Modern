package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FactoryBlockPattern {

    private final List<String[]> depth;
    private final List<int[]> aisleRepetitions;
    private final Char2ObjectOpenHashMap<TraceabilityPredicate> symbolMap;
    private final RelativeDirection[] structureDir;
    private int aisleHeight;
    private int rowWidth;

    private FactoryBlockPattern(RelativeDirection charDir, RelativeDirection stringDir, RelativeDirection aisleDir) {
        depth = new ArrayList<>();
        aisleRepetitions = new ArrayList<>();
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

    private FactoryBlockPattern aisleRepeatable(int minRepeat, int maxRepeat, String... aisle) {
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

    public static FactoryBlockPattern start(RelativeDirection charDir, RelativeDirection stringDir,
                                            RelativeDirection aisleDir) {
        return new FactoryBlockPattern(charDir, stringDir, aisleDir);
    }

    public FactoryBlockPattern where(char symbol, TraceabilityPredicate blockMatcher) {
        if (blockMatcher.isAny()) return this;
        if (blockMatcher.isAir()) {
            this.symbolMap.put(symbol, TraceabilityPredicate.AIR);
        } else {
            this.symbolMap.put(symbol, blockMatcher.sort());
        }
        return this;
    }

    public static TraceabilityPredicate utilityExcluding(TraceabilityPredicate utilityAbilities, TraceabilityPredicate exclusions) {
        var excludedBlocks = candidateBlocks(exclusions, "wherePart exclusions");
        var allowedUtilities = new TraceabilityPredicate();
        for (SimplePredicate predicate : utilityAbilities.common) {
            var candidates = candidateBlocks(predicate, "utility ability");
            var allowed = candidates.stream()
                    .filter(block -> !excludedBlocks.contains(block))
                    .toArray(Block[]::new);
            if (allowed.length > 0) {
                allowedUtilities = allowedUtilities.or(Predicates.blocks(allowed).setPreviewCount(0));
            }
        }
        for (SimplePredicate predicate : utilityAbilities.limited) {
            var candidates = candidateBlocks(predicate, "utility ability");
            if (Collections.disjoint(candidates, excludedBlocks)) {
                allowedUtilities.limited.add(predicate);
            } else if (!excludedBlocks.containsAll(candidates)) {
                throw new IllegalArgumentException(
                        "wherePart exclusions cannot partially exclude a globally limited utility ability");
            }
        }
        return allowedUtilities;
    }

    private static ReferenceOpenHashSet<Block> candidateBlocks(TraceabilityPredicate predicate, String description) {
        var blocks = new ReferenceOpenHashSet<Block>();
        for (SimplePredicate simplePredicate : predicate.common) {
            blocks.addAll(candidateBlocks(simplePredicate, description));
        }
        for (SimplePredicate simplePredicate : predicate.limited) {
            blocks.addAll(candidateBlocks(simplePredicate, description));
        }
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException(description + " must provide at least one block candidate");
        }
        return blocks;
    }

    private static List<Block> candidateBlocks(SimplePredicate predicate, String description) {
        if (predicate.candidates == null) {
            throw new IllegalArgumentException(description + " must provide enumerable block candidates");
        }
        var candidates = predicate.candidates.get();
        if (candidates == null) {
            throw new IllegalArgumentException(description + " returned null block candidates");
        }
        return Arrays.asList(candidates);
    }

    public BlockPattern build() {
        int size = this.depth.size();
        int[] centerOffset = new int[5];
        int[][] aisleRepetitions = this.aisleRepetitions.toArray(new int[this.aisleRepetitions.size()][]);
        TraceabilityPredicate[][][] predicate = new TraceabilityPredicate[size][][];

        for (int i = 0, minZ = 0, maxZ = 0; i < size; minZ += aisleRepetitions[i][0], maxZ += aisleRepetitions[i][1], i++) {
            for (int j = 0; j < this.aisleHeight; j++) {
                for (int k = 0; k < this.rowWidth; k++) {
                    var tp = this.symbolMap.get(this.depth.get(i)[j].charAt(k));
                    if (tp != null) {
                        var pi = predicate[i];
                        if (pi == null) {
                            predicate[i] = pi = new TraceabilityPredicate[this.aisleHeight][];
                        }
                        var pj = pi[j];
                        if (pj == null) {
                            pi[j] = pj = new TraceabilityPredicate[this.rowWidth];
                        }
                        pj[k] = tp;
                        if (tp instanceof ControllerPredicate) centerOffset = new int[] { k, j, i, minZ, maxZ };
                    }
                }
            }
        }

        return new BlockPattern(predicate, structureDir, aisleRepetitions, centerOffset, size, this.aisleHeight, this.rowWidth);
    }
}
