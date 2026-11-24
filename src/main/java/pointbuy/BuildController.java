package pointbuy;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BuildController {

    private final BuildStore store;

    public BuildController(BuildStore store) {
        this.store = store;
    }

    @Operation(summary = "List the 16 PHB backgrounds and the abilities each can raise")
    @GetMapping("/backgrounds")
    public List<Background> backgrounds() {
        return PointBuyRules.BACKGROUNDS;
    }

    @Operation(summary = "Evaluate a build without saving it",
            description = "Always 200: totals, modifiers, points spent and remaining, plus a list of rule problems.")
    @PostMapping("/builds/evaluate")
    public BuildResult evaluate(@RequestBody BuildRequest r) {
        return PointBuyRules.evaluate(r, false);
    }

    @Operation(summary = "Save a named build",
            description = "201 with the saved build if it is legal; 400 with the evaluation (see problems) if not.")
    @PostMapping("/builds")
    public ResponseEntity<BuildResult> save(@RequestBody BuildRequest r) {
        BuildResult result = PointBuyRules.evaluate(r, true);
        if (!result.problems().isEmpty()) {
            return ResponseEntity.badRequest().body(result);
        }
        SavedBuild saved = store.save(r);
        return ResponseEntity.status(HttpStatus.CREATED).body(result.saved(saved.id(), saved.savedAt()));
    }

    @Operation(summary = "List all saved builds, newest first")
    @GetMapping("/builds")
    public List<BuildResult> all() {
        return store.all().stream()
                .map(s -> PointBuyRules.evaluate(s.request(), false).saved(s.id(), s.savedAt()))
                .toList();
    }
}
