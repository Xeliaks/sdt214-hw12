package pointbuy;

import static org.junit.jupiter.api.Assertions.*;
import static pointbuy.Ability.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildStoreTest {

    @TempDir Path dir;
    Path file;

    static final BuildRequest MIRA = new BuildRequest("Mira", "Sage",
            Map.of(STR, 8, DEX, 14, CON, 13, INT, 15, WIS, 12, CHA, 10), Map.of(INT, 2, WIS, 1));

    @BeforeEach
    void setUp() {
        file = dir.resolve("builds.json");
    }

    @Test
    void missingFileIsEmpty() {
        assertTrue(new BuildStore(file.toString()).all().isEmpty());
    }

    @Test
    void saveThenReloadInNewStore() {
        var a = new BuildStore(file.toString());
        assertEquals(1, a.save(MIRA).id());
        assertEquals(2, a.save(MIRA).id());
        var b = new BuildStore(file.toString());
        assertEquals(List.of(2L, 1L), b.all().stream().map(SavedBuild::id).toList());
        assertEquals(MIRA, b.all().get(0).request());
        assertEquals(3, b.save(MIRA).id());
    }

    @Test
    void corruptFileRefusesToStart() throws Exception {
        for (String junk : List.of("{not json", "", "null")) {
            Files.writeString(file, junk);
            var e = assertThrows(IllegalStateException.class, () -> new BuildStore(file.toString()));
            assertTrue(e.getMessage().contains("Fix or delete it"));
            assertEquals(junk, Files.readString(file));
        }
    }
}
