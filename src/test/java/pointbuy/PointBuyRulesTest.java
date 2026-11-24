package pointbuy;

import static org.junit.jupiter.api.Assertions.*;
import static pointbuy.Ability.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PointBuyRulesTest {

    static final Map<Ability, Integer> SCORES = Map.of(STR, 8, DEX, 14, CON, 13, INT, 15, WIS, 12, CHA, 10);

    static BuildResult eval(String bg, Map<Ability, Integer> scores, Map<Ability, Integer> bonuses) {
        return PointBuyRules.evaluate(new BuildRequest("Mira", bg, scores, bonuses), false);
    }

    @Test
    void costTable() {
        assertEquals(0, PointBuyRules.cost(8));
        assertEquals(5, PointBuyRules.cost(13));
        assertEquals(7, PointBuyRules.cost(14));
        assertEquals(9, PointBuyRules.cost(15));
        assertThrows(IllegalArgumentException.class, () -> PointBuyRules.cost(7));
        assertThrows(IllegalArgumentException.class, () -> PointBuyRules.cost(16));
    }

    @Test
    void modifiers() {
        assertEquals(-1, PointBuyRules.modifier(8));
        assertEquals(-1, PointBuyRules.modifier(9));
        assertEquals(0, PointBuyRules.modifier(10));
        assertEquals(3, PointBuyRules.modifier(17));
    }

    @Test
    void sixteenBackgrounds() {
        assertEquals(16, PointBuyRules.BACKGROUNDS.size());
        assertEquals(List.of(CON, INT, WIS), PointBuyRules.background("Sage").orElseThrow().abilities());
        assertEquals(List.of(STR, DEX, CON), PointBuyRules.background("Soldier").orElseThrow().abilities());
    }

    @Test
    void legalBuild() {
        BuildResult r = eval("Sage", SCORES, Map.of(INT, 2, WIS, 1));
        assertEquals(List.of(), r.problems());
        assertEquals(27, r.pointsSpent());
        assertEquals(0, r.pointsRemaining());
        assertEquals(List.of(STR, DEX, CON, INT, WIS, CHA), r.abilities().stream().map(AbilityLine::ability).toList());
        assertEquals(new AbilityLine(INT, 15, 2, 17, 3, 9), r.abilities().get(3));
    }

    @Test
    void oneOneOne() {
        assertEquals(List.of(), eval("Sage", SCORES, Map.of(CON, 1, INT, 1, WIS, 1)).problems());
    }

    @Test
    void wrongAbility() {
        assertTrue(eval("Sage", SCORES, Map.of(STR, 2, INT, 1)).problems().contains("Sage can't raise STR"));
    }

    @Test
    void badAmounts() {
        String msg = "Bonuses must be +2/+1 or +1/+1/+1";
        assertTrue(eval("Sage", SCORES, Map.of(INT, 3)).problems().contains(msg));
        assertTrue(eval("Sage", SCORES, Map.of(INT, 2, WIS, 2)).problems().contains(msg));
        assertTrue(eval("Sage", SCORES, Map.of()).problems().contains(msg));
    }

    @Test
    void zeroBonusIgnored() {
        assertEquals(List.of(), eval("Sage", SCORES, Map.of(STR, 0, INT, 2, WIS, 1)).problems());
    }

    @Test
    void overBudget() {
        var all15 = Map.of(STR, 15, DEX, 15, CON, 15, INT, 15, WIS, 15, CHA, 15);
        BuildResult r = eval("Sage", all15, Map.of(INT, 2, WIS, 1));
        assertTrue(r.problems().contains("Over budget by 27"));
        assertEquals(-27, r.pointsRemaining());
    }

    @Test
    void outOfRange() {
        var scores = new java.util.HashMap<>(SCORES);
        scores.put(STR, 16);
        BuildResult r = eval("Sage", scores, Map.of(INT, 2, WIS, 1));
        assertTrue(r.problems().contains("STR must be 8–15"));
        assertEquals(new AbilityLine(STR, 16, 0, 16, 3, 0), r.abilities().get(0));
    }

    @Test
    void nullMaps() {
        BuildResult r = PointBuyRules.evaluate(new BuildRequest(null, "Sage", null, null), false);
        assertEquals(6, r.problems().stream().filter(p -> p.endsWith("must be 8–15")).count());
        assertTrue(r.problems().contains("Bonuses must be +2/+1 or +1/+1/+1"));
        assertEquals(8, r.abilities().get(0).base());
    }

    @Test
    void backgroundProblems() {
        assertTrue(eval(null, SCORES, Map.of(INT, 2, WIS, 1)).problems().contains("Choose a background"));
        assertTrue(eval(" ", SCORES, Map.of(INT, 2, WIS, 1)).problems().contains("Choose a background"));
        assertTrue(eval("sage", SCORES, Map.of(INT, 2, WIS, 1)).problems().contains("Unknown background: sage"));
    }

    @Test
    void nameRequiredOnlyForSave() {
        var req = new BuildRequest(" ", "Sage", SCORES, Map.of(INT, 2, WIS, 1));
        assertEquals(List.of(), PointBuyRules.evaluate(req, false).problems());
        assertEquals(List.of("Name is required"), PointBuyRules.evaluate(req, true).problems());
    }
}
