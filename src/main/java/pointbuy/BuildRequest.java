package pointbuy;

import java.util.Map;

/** A build as clients send it: base scores and the chosen background bonuses. */
public record BuildRequest(String name, String background,
                           Map<Ability, Integer> scores, Map<Ability, Integer> bonuses) {}
