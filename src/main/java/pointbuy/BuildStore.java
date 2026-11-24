package pointbuy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Keeps saved builds in a JSON file. */
@Component
public class BuildStore {

    private final ObjectMapper json = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final Path file;
    private final List<SavedBuild> builds; // oldest first

    public BuildStore(@Value("${pointbuy.file:builds.json}") String file) {
        this.file = Path.of(file);
        this.builds = load();
    }

    public synchronized SavedBuild save(BuildRequest r) {
        long id = builds.stream().mapToLong(SavedBuild::id).max().orElse(0) + 1;
        SavedBuild build = new SavedBuild(id, Instant.now(), r.name(), r.background(), r.scores(), r.bonuses());

        List<SavedBuild> updated = new ArrayList<>(builds);
        updated.add(build);
        write(updated); // keep memory and file in step: only add once it's on disk
        builds.add(build);
        return build;
    }

    /** All builds, newest first. */
    public synchronized List<SavedBuild> all() {
        return List.copyOf(builds.reversed());
    }

    private List<SavedBuild> load() {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        List<SavedBuild> loaded;
        try {
            loaded = json.readValue(file.toFile(), new TypeReference<List<SavedBuild>>() {});
        } catch (IOException e) {
            throw unreadable(e.getMessage(), e);
        }
        if (loaded == null) {
            throw unreadable("file contains null", null);
        }
        return new ArrayList<>(loaded);
    }

    private IllegalStateException unreadable(String reason, Exception cause) {
        return new IllegalStateException(
                "Cannot read " + file.toAbsolutePath() + ": " + reason + ". Fix or delete it.", cause);
    }

    private void write(List<SavedBuild> data) {
        Path tmp = Path.of(file + ".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), data);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save " + file.toAbsolutePath(), e);
        }
    }
}
