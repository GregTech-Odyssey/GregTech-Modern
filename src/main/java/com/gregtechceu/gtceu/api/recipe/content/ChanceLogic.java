package com.gregtechceu.gtceu.api.recipe.content;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Logic for determining which chanced outputs should be produced from a list
 */
public abstract class ChanceLogic {

    /**
     * Chanced Output Logic where any ingredients succeeding their roll will be produced
     */
    public static final ChanceLogic OR = new ChanceLogic("or") {

        @Override
        public @NotNull Component getTranslation() {
            return Component.translatable("gtceu.chance_logic.or");
        }

        @Override
        public String toString() {
            return "ChanceLogic{OR}";
        }
    };

    /**
     * Chanced Output Logic where all ingredients must succeed their roll in order for any to be produced
     */
    public static final ChanceLogic AND = new ChanceLogic("and") {

        @Override
        public @NotNull Component getTranslation() {
            return Component.translatable("gtceu.chance_logic.and");
        }

        @Override
        public String toString() {
            return "ChanceLogic{AND}";
        }
    };

    /**
     * Chanced Output Logic where only the first ingredient succeeding its roll will be produced
     */
    public static final ChanceLogic FIRST = new ChanceLogic("first") {

        @Override
        public @NotNull Component getTranslation() {
            return Component.translatable("gtceu.chance_logic.first");
        }

        @Override
        public String toString() {
            return "ChanceLogic{FIRST}";
        }
    };

    /**
     * Chanced Output Logic where only one of the ingredients will be output, in a manner weighted to the input chances
     */
    public static final ChanceLogic XOR = new ChanceLogic("xor") {

        @Override
        public @NotNull Component getTranslation() {
            return Component.translatable("gtceu.chance_logic.xor");
        }

        @Override
        public String toString() {
            return "ChanceLogic{XOR}";
        }
    };

    /**
     * Chanced Output Logic where nothing is produced
     */
    public static final ChanceLogic NONE = new ChanceLogic("none") {

        @Override
        public @NotNull Component getTranslation() {
            return Component.translatable("gtceu.chance_logic.none");
        }

        @Override
        public String toString() {
            return "ChanceLogic{NONE}";
        }
    };

    public ChanceLogic(String id) {}

    @NotNull
    public abstract Component getTranslation();

    @ApiStatus.Internal
    public static void init() {}
}
