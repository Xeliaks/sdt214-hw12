package pointbuy;

/** One row of the calculator table. */
public record AbilityLine(Ability ability, int base, int bonus, int total, int modifier, int cost) {}
