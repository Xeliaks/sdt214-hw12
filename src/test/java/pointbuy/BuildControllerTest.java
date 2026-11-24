package pointbuy;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static pointbuy.Ability.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD) // fresh store per test
class BuildControllerTest {

    @TempDir static Path dir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("pointbuy.file", () -> dir.resolve("builds.json").toString());
    }

    @Autowired TestRestTemplate rest;

    static final Map<Ability, Integer> SCORES = Map.of(STR, 8, DEX, 14, CON, 13, INT, 15, WIS, 12, CHA, 10);

    static BuildRequest build(String name) {
        return new BuildRequest(name, "Sage", SCORES, Map.of(INT, 2, WIS, 1));
    }

    @BeforeEach
    void clean() throws Exception {
        Files.deleteIfExists(dir.resolve("builds.json"));
    }

    @Test
    void backgroundsListed() {
        assertEquals(16, rest.getForObject("/backgrounds", Background[].class).length);
        assertTrue(rest.getForObject("/backgrounds", String.class).contains("\"Sage\""));
    }

    @Test
    void evaluateReportsProblemsWith200() {
        var all15 = Map.of(STR, 15, DEX, 15, CON, 15, INT, 15, WIS, 15, CHA, 15);
        var r = rest.postForEntity("/builds/evaluate",
                new BuildRequest(null, "Sage", all15, Map.of(INT, 2, WIS, 1)), BuildResult.class);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertTrue(r.getBody().problems().contains("Over budget by 27"));
        assertNull(r.getBody().id());
    }

    @Test
    void saveLegal201ThenListed() {
        var r = rest.postForEntity("/builds", build("Mira"), BuildResult.class);
        assertEquals(HttpStatus.CREATED, r.getStatusCode());
        assertEquals(1L, r.getBody().id());
        assertNotNull(r.getBody().savedAt());
        var all = rest.getForObject("/builds", BuildResult[].class);
        assertEquals("Mira", all[0].name());
        assertEquals(17, all[0].abilities().get(3).total());
    }

    @Test
    void saveIllegal400NoWrite() {
        var r = rest.postForEntity("/builds", build(" "), BuildResult.class);
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertTrue(r.getBody().problems().contains("Name is required"));
        assertEquals(0, rest.getForObject("/builds", BuildResult[].class).length);
        assertEquals(1L, rest.postForEntity("/builds", build("Mira"), BuildResult.class).getBody().id());
    }

    @Test
    void unknownAbilityKey400() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = new HttpEntity<>("{\"background\":\"Sage\",\"scores\":{\"FOO\":8}}", headers);
        assertEquals(HttpStatus.BAD_REQUEST, rest.postForEntity("/builds/evaluate", body, String.class).getStatusCode());
    }

    @Test
    void pagesServed() {
        String page = rest.getForObject("/", String.class);
        assertTrue(page.contains("<title>5e Point Buy Calculator</title>"));
        assertTrue(page.contains("swagger-ui.html"));
        String spec = rest.getForObject("/v3/api-docs", String.class);
        assertTrue(spec.contains("\"/builds/evaluate\""));
        assertTrue(spec.contains("Point Buy API"));
    }
}
