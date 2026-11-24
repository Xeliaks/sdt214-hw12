# 5e Point Buy Calculator

A REST web service (Spring Boot) and clients for building D&D 5e ability scores
with the **2024 Player's Handbook** point-buy rules:

- 27 points. Every score starts at 8 and can be 8–15 before bonuses.
- Costs: 8→0, 9→1, 10→2, 11→3, 12→4, 13→5, 14→7, 15→9.
- Ability bonuses come from your **background** (2024 rules; species give none):
  +2/+1 or +1/+1/+1 among the three abilities the background lists.

The service does all the math and checks the rules. Named builds can be saved to
`builds.json`.

## Requirements

Java 21. Gradle is included via the wrapper (`gradlew`).

## Run

```
gradlew bootRun       # start the service on http://localhost:8090
gradlew runClient     # Swing desktop client (in a second terminal)
gradlew test          # run the tests
```

With the service running:

| Page | URL |
|---|---|
| Web client | http://localhost:8090/ |
| API page (Swagger UI) | http://localhost:8090/swagger-ui.html |

## API

| Method | Path | Result |
|---|---|---|
| `GET` | `/backgrounds` | The 16 PHB backgrounds and their abilities. |
| `POST` | `/builds/evaluate` | Evaluates a build. Always `200`, with any rule problems listed. |
| `POST` | `/builds` | Saves a named build. `201` if legal, `400` with `problems` if not. |
| `GET` | `/builds` | All saved builds, newest first. |

Request:

```json
{
  "name": "Mira the Sage",
  "background": "Sage",
  "scores":  {"STR": 8, "DEX": 14, "CON": 13, "INT": 15, "WIS": 12, "CHA": 10},
  "bonuses": {"INT": 2, "WIS": 1}
}
```

Response (shortened to two of the six abilities):

```json
{
  "name": "Mira the Sage",
  "background": "Sage",
  "abilities": [
    {"ability": "STR", "base": 8,  "bonus": 0, "total": 8,  "modifier": -1, "cost": 0},
    {"ability": "INT", "base": 15, "bonus": 2, "total": 17, "modifier": 3,  "cost": 9}
  ],
  "pointsSpent": 27,
  "pointsRemaining": 0,
  "problems": []
}
```

Saved builds also have `id` and `savedAt`. Problems are plain sentences, for example
`"Over budget by 3"` or `"Sage can't raise STR"`.

## Code

All code is in `src/main/java/pointbuy/`.

| File | Role |
|---|---|
| `PointBuyApplication` | Spring Boot entry point |
| `BuildController` | REST endpoints |
| `PointBuyRules` | Costs, modifiers, backgrounds, validation |
| `BuildStore` | Saves builds to `builds.json` |
| `BuildRequest` / `BuildResult` / `AbilityLine` | API input and output |
| `SavedBuild` | Stored form of a build (inputs only) |
| `Ability` / `Background` | The six abilities and the 16 backgrounds |
| `PointBuyClient` | Swing client (Java `HttpClient` + Jackson) |

The web client is in `src/main/resources/static/index.html`.
