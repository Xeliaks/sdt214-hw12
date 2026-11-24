package pointbuy;

import static pointbuy.Ability.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** D&D 5e (2024 PHB) point buy: costs, modifiers, backgrounds and build validation. */
public final class PointBuyRules {

    public static final int BUDGET = 27;
    public static final int MIN = 8;
    public static final int MAX = 15;

    private static final int[] COST = {0, 1, 2, 3, 4, 5, 7, 9}; // scores 8..15

    public static final List<Background> BACKGROUNDS = List.of(
            new Background("Acolyte", List.of(INT, WIS, CHA)),
            new Background("Artisan", List.of(STR, DEX, INT)),
            new Background("Charlatan", List.of(DEX, CON, CHA)),
            new Background("Criminal", List.of(DEX, CON, INT)),
            new Background("Entertainer", List.of(STR, DEX, CHA)),
            new Background("Farmer", List.of(STR, CON, WIS)),
            new Background("Guard", List.of(STR, INT, WIS)),
            new Background("Guide", List.of(DEX, CON, WIS)),
            new Background("Hermit", List.of(CON, WIS, CHA)),
            new Background("Merchant", List.of(CON, INT, CHA)),
            new Background("Noble", List.of(STR, INT, CHA)),
            new Background("Sage", List.of(CON, INT, WIS)),
            new Background("Sailor", List.of(STR, DEX, WIS)),
            new Background("Scribe", List.of(DEX, INT, WIS)),
            new Background("Soldier", List.of(STR, DEX, CON)),
            new Background("Wayfarer", List.of(DEX, WIS, CHA)));

    private PointBuyRules() {}

    public static int cost(int score) {
        if (score < MIN || score > MAX) {
            throw new IllegalArgumentException("Score out of range 8..15: " + score);
        }
        return COST[score - MIN];
    }

    public static int modifier(int total) {
        return Math.floorDiv(total - 10, 2);
    }

    /** Exact (case-sensitive) name match. */
    public static Optional<Background> background(String name) {
        return BACKGROUNDS.stream().filter(b -> b.name().equals(name)).findFirst();
    }

    /** Works out every row and lists what is wrong. Never throws for odd input. */
    public static BuildResult evaluate(BuildRequest r, boolean requireName) {
        Map<Ability, Integer> scores = r.scores() == null ? Map.of() : r.scores();
        Map<Ability, Integer> bonuses = r.bonuses() == null ? Map.of() : r.bonuses();
        List<String> problems = new ArrayList<>();

        List<AbilityLine> lines = new ArrayList<>();
        int spent = 0;
        for (Ability a : Ability.values()) {
            Integer given = scores.get(a);
            int base = given == null ? MIN : given;
            int cost = 0;
            if (given == null || base < MIN || base > MAX) {
                problems.add(a + " must be 8–15");
            } else {
                cost = cost(base);
            }
            Integer b = bonuses.get(a);
            int bonus = b == null ? 0 : b;
            spent += cost;
            lines.add(new AbilityLine(a, base, bonus, base + bonus, modifier(base + bonus), cost));
        }
        if (spent > BUDGET) {
            problems.add("Over budget by " + (spent - BUDGET));
        }

        Optional<Background> bg = Optional.empty();
        if (r.background() == null || r.background().isBlank()) {
            problems.add("Choose a background");
        } else {
            bg = background(r.background());
            if (bg.isEmpty()) {
                problems.add("Unknown background: " + r.background());
            }
        }

        List<Ability> raised = lines.stream().filter(l -> l.bonus() != 0).map(AbilityLine::ability).toList();
        List<Integer> amounts = lines.stream().map(AbilityLine::bonus).filter(b -> b != 0).sorted().toList();
        if (!amounts.equals(List.of(1, 2)) && !amounts.equals(List.of(1, 1, 1))) {
            problems.add("Bonuses must be +2/+1 or +1/+1/+1");
        }
        bg.ifPresent(b -> raised.stream()
                .filter(a -> !b.abilities().contains(a))
                .forEach(a -> problems.add(b.name() + " can't raise " + a)));

        if (requireName && (r.name() == null || r.name().isBlank())) {
            problems.add("Name is required");
        }
        return new BuildResult(null, null, r.name(), r.background(), lines, spent, BUDGET - spent, problems);
    }
}
