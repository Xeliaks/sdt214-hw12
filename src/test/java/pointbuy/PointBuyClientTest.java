package pointbuy;

import static org.junit.jupiter.api.Assertions.*;
import static pointbuy.Ability.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PointBuyClientTest {

    static final Background SAGE = new Background("Sage", java.util.List.of(CON, INT, WIS));

    @Test
    void bonusesTwoOne() {
        assertEquals(Map.of(INT, 2, WIS, 1), PointBuyClient.bonuses(SAGE, true, INT, WIS));
    }

    @Test
    void bonusesOneOneOne() {
        assertEquals(Map.of(CON, 1, INT, 1, WIS, 1), PointBuyClient.bonuses(SAGE, false, null, null));
    }

    @Test
    void bonusesWithoutBackgroundIsEmpty() {
        assertEquals(Map.of(), PointBuyClient.bonuses(null, true, null, null));
    }

    @Test
    void signed() {
        assertEquals("+3", PointBuyClient.signed(3));
        assertEquals("-1", PointBuyClient.signed(-1));
        assertEquals("+0", PointBuyClient.signed(0));
    }
}
