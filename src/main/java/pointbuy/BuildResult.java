package pointbuy;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** An evaluated build. id and savedAt are set only for saved builds. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuildResult(Long id, Instant savedAt, String name, String background,
                          List<AbilityLine> abilities, int pointsSpent, int pointsRemaining,
                          List<String> problems) {

    BuildResult saved(long id, Instant savedAt) {
        return new BuildResult(id, savedAt, name, background, abilities, pointsSpent, pointsRemaining, problems);
    }
}
