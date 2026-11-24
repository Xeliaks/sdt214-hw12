package pointbuy;

import java.time.Instant;
import java.util.Map;

/** A saved build: only the inputs. This is what goes into builds.json. */
public record SavedBuild(long id, Instant savedAt, String name, String background,
                         Map<Ability, Integer> scores, Map<Ability, Integer> bonuses) {

    public BuildRequest request() {
        return new BuildRequest(name, background, scores, bonuses);
    }
}
