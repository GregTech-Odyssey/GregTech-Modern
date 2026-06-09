package com.gregtechceu.gtceu.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GTMathTest {

    @Test
    void saturatedMultiplyKeepsNonOverflowingProduct() {
        assertEquals(12_000L, GTMath.saturatedMultiply(3_000, 4));
    }

    @Test
    void saturatedMultiplyKeepsExactLongMaximumProduct() {
        assertEquals(Long.MAX_VALUE, GTMath.saturatedMultiply(Long.MAX_VALUE, 1));
    }

    @Test
    void saturatedMultiplyCapsPositiveLongOverflow() {
        assertEquals(Long.MAX_VALUE, GTMath.saturatedMultiply(Long.MAX_VALUE / 2 + 1, 2));
    }

    @Test
    void saturatedMultiplyCapsNegativeLongOverflow() {
        assertEquals(Long.MIN_VALUE, GTMath.saturatedMultiply(Long.MAX_VALUE / 2 + 1, -2));
    }
}
