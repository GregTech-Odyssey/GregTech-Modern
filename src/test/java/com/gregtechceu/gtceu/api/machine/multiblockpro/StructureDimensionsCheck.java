package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.pattern.ControllerPredicate;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.targets.ForgeDataUserdevLaunchHandler;

import sun.reflect.ReflectionFactory;

import java.util.Arrays;
import java.util.SplittableRandom;

import static com.gregtechceu.gtceu.api.machine.multiblockpro.PortKey.IN;
import static com.gregtechceu.gtceu.api.machine.multiblockpro.PortKey.OUT;
import static com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.*;

/** Differential checks against the full layout, including proof that dimensions do not compile predicates. */
public final class StructureDimensionsCheck {

    static {
        try {
            // No world, network or blocks are used; initialize only the registry containers and physical side.
            var bootstrap = Bootstrap.class.getDeclaredField("isBootstrapped");
            bootstrap.setAccessible(true);
            bootstrap.setBoolean(null, true);
            SharedConstants.tryDetectVersion();
            var dist = FMLLoader.class.getDeclaredField("dist");
            dist.setAccessible(true);
            dist.set(null, Dist.DEDICATED_SERVER);
            var handler = FMLLoader.class.getDeclaredField("commonLaunchHandler");
            handler.setAccessible(true);
            handler.set(null, new ForgeDataUserdevLaunchHandler());
            Class.forName("net.minecraft.core.registries.BuiltInRegistries");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final ParamKey COUNT = ParamKey.of("test.count");
    private static final ParamKey WIDTH = ParamKey.of("test.width");
    private static final PortKey SIDE = PortKey.of("side");
    private static final CountingPredicate BLOCK = new CountingPredicate();
    private static final TraceabilityPredicate CONTROLLER = controller();
    private static int checked;

    private StructureDimensionsCheck() {}

    public static void main(String[] args) {
        var random = new SplittableRandom(20261021);
        for (var charDir : RelativeDirection.values()) {
            for (var stringDir : RelativeDirection.values()) {
                if (stringDir == charDir || stringDir == charDir.getOpposite()) continue;
                for (var aisleDir : RelativeDirection.values()) {
                    if (aisleDir == charDir || aisleDir == charDir.getOpposite() ||
                            aisleDir == stringDir || aisleDir == stringDir.getOpposite())
                        continue;
                    for (int i = 0; i < 10; i++) {
                        var builder = Piece.start(charDir, stringDir, aisleDir);
                        for (int c = 0; c < 3; c++) {
                            var rows = new String[4];
                            for (int b = 0; b < rows.length; b++) {
                                var row = new char[7];
                                for (int a = 0; a < row.length; a++) row[a] = " X?_".charAt(random.nextInt(4));
                                if (c == 1 && b == 2) row[3] = 'S';
                                rows[b] = new String(row);
                            }
                            builder.aisle(rows);
                        }
                        verify(Structure.root(builder.build()).symbols(symbols()).build());
                    }
                }
            }
        }
        var root = Piece.start(RIGHT, UP, BACK).aisle("SXX", "XXX").portBefore(IN).portAfter(OUT)
                .port('S', SIDE, LEFT).build();
        for (var facing : RelativeDirection.values()) {
            var module = Piece.start(LEFT, DOWN, FRONT).aisle("PXX", "XXX").aisle("XXX", "XXQ")
                    .port('P', IN, facing).port('Q', OUT, facing.getOpposite()).build();
            for (int count = 0; count <= 5; count++) {
                var chain = Slot.chain(module, IN, OUT, IN).count(COUNT, count, Math.max(1, count))
                        .atPort(OUT, Slot.one(module, IN));
                verify(Structure.root(root).symbols(symbols()).atPort(SIDE, chain).build());
            }
            verify(Structure.root(root).symbols(symbols()).atPort(OUT, Slot.optional(module, IN)).build());
            verify(Structure.root(root).symbols(symbols()).atPort(OUT, Slot.choice(Slot.one(module, IN), Slot.one(module, OUT))).build());
            verify(Structure.root(root).symbols(symbols()).atPort(OUT, Slot.choice(Slot.one(module, IN)).optional()).build());
        }
        // Turning reflexive ports exercise rotation changes and overlap within a repeated chain.
        for (var entry : RelativeDirection.values()) {
            for (var exit : RelativeDirection.values()) {
                var turning = Piece.start(RIGHT, UP, BACK).aisle("PXX", "XXQ")
                        .port('P', IN, entry).port('Q', OUT, exit).build();
                verify(Structure.root(root).symbols(symbols()).atPort(SIDE,
                        Slot.chain(turning, IN, OUT, IN).count(COUNT, 4, 4).atPort(OUT, Slot.one(rootless(), IN))).build());
            }
        }
        var empty = Piece.start(RIGHT, UP, BACK).aisle("???").portBefore(IN).portAfter(OUT).build();
        verify(Structure.root(root).symbols(symbols()).atPort(OUT, Slot.one(empty, IN).atPort(OUT, Slot.one(rootless(), IN))).build());
        verify(Structure.root(root).symbols(symbols()).atPort(OUT, Slot.one(rootless(), IN))
                .atPort(OUT, Slot.one(rootless(), IN)).build());
        // A default zero-length chain forwards its OUT child to the original host port.
        verify(Structure.root(root).symbols(symbols()).atPort(OUT,
                Slot.chain(rootless(), IN, OUT, IN).count(COUNT, 0, 5).atPort(OUT, Slot.one(rootless(), IN))).build());
        for (int preset : new int[] { 1, 4, 9 }) {
            var sized = Piece.sized(size -> Piece.start(RIGHT, UP, BACK)
                    .aisle("S" + "X".repeat(size.get(WIDTH) - 1)).portAfter(OUT).build());
            verify(Structure.root(sized).symbols(symbols())
                    .measure(m -> m.param(WIDTH).toward(RIGHT).until('X').range(1, 10).preset(preset))
                    .atPort(OUT, Slot.one(Piece.sized(size -> Piece.start(RIGHT, UP, BACK)
                            .aisle("X".repeat(size.get(WIDTH) + 2)).portBefore(IN).build()), IN))
                    .build());
        }
        for (int span : new int[] { 64, 65 }) {
            var giant = Structure.root(Piece.start(RIGHT, UP, BACK).aisle("S" + "X".repeat(span - 1)).build()).symbols(symbols()).build();
            verify(giant);
            if ((Math.max(giant.getWidth(), giant.getDepth()) > 64) != (span == 65)) throw new AssertionError("Giant threshold changed");
        }
        var noCompile = Structure.root(root).symbols(symbols()).limit(BLOCK, size -> {
            throw new AssertionError("Dimensions compiled the structure's limits");
        }).build();
        if (noCompile.getWidth() != 3 || noCompile.getHeight() != 2 || noCompile.getDepth() != 1) throw new AssertionError("Wrong uncompiled dimensions");
        var rejected = Structure.root(root).symbols(symbols()).require(size -> false).build();
        if (rejected.getWidth() != 0 || rejected.getHeight() != 0 || rejected.getDepth() != 0) throw new AssertionError("Rejected defaults have dimensions");
        System.out.println("StructureDimensionsCheck PASS: " + checked + " layouts");
    }

    private static Piece rootless() {
        return Piece.start(RIGHT, UP, BACK).aisle("XXX").aisle("XXX").portBefore(IN).portAfter(OUT).build();
    }

    private static Symbols symbols() {
        return Symbols.create().where('S', CONTROLLER).where('X', BLOCK).where('P', BLOCK).where('Q', BLOCK)
                .where('?', new TraceabilityPredicate(SimplePredicate.ANY)).where('_', TraceabilityPredicate.AIR);
    }

    private static void verify(Structure structure) {
        BLOCK.sorts = 0;
        int[] actual = { structure.getWidth(), structure.getHeight(), structure.getDepth() };
        if (BLOCK.sorts != 0) throw new AssertionError("Dimensions compiled predicates");
        var layout = structure.layout(structure.defaultValues());
        int[] expected = { layout.width(), layout.height(), layout.depth() };
        if (!Arrays.equals(expected, actual)) throw new AssertionError("Expected " + Arrays.toString(expected) + ", got " + Arrays.toString(actual));
        checked++;
    }

    private static TraceabilityPredicate controller() {
        // Layout only needs the controller marker. Initialize its predicate superclass without a game registry.
        try {
            var constructor = ReflectionFactory.getReflectionFactory().newConstructorForSerialization(
                    ControllerPredicate.class, TraceabilityPredicate.class.getConstructor());
            var predicate = (TraceabilityPredicate) constructor.newInstance();
            predicate.common.add(new SimplePredicate());
            return predicate;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class CountingPredicate extends TraceabilityPredicate {

        int sorts;

        @Override
        public TraceabilityPredicate sort() {
            sorts++;
            return this;
        }
    }
}
