package pointbuy;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The REST service. Start with: gradlew bootRun
 * Web page: http://localhost:8090/   API page: http://localhost:8090/swagger-ui.html
 */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Point Buy API", version = "1.0",
        description = "D&D 5e (2024) point-buy calculator: 27 points, scores 8–15, background bonuses."))
public class PointBuyApplication {
    public static void main(String[] args) {
        SpringApplication.run(PointBuyApplication.class, args);
    }
}
