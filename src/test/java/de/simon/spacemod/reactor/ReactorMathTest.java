package de.simon.spacemod.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReactorMathTest {
    @Test
    void largestReactorMakesTenThousand() {
        assertEquals(10_000, ReactorMath.output(8));
        assertEquals(0, ReactorMath.output(2));
    }

    @Test
    void slotsFromOneToNine() {
        assertEquals(1, ReactorMath.slots(3));
        assertEquals(9, ReactorMath.slots(8));
        for (int n = 4; n <= 8; n++) assertTrue(ReactorMath.slots(n) >= ReactorMath.slots(n - 1));
    }

    @Test
    void biggerBurnsFasterAndMakesMore() {
        for (int n = 4; n <= 8; n++) {
            assertTrue(ReactorMath.burnTicks(n) < ReactorMath.burnTicks(n - 1));
            assertTrue(ReactorMath.output(n) > ReactorMath.output(n - 1));
        }
    }
}
