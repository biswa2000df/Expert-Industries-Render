package expert.industries.render.Config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;


@Configuration
@EnableWebMvc
@SecurityScheme(
        name = "Expert Industries API",
        type = SecuritySchemeType.HTTP,
        scheme="basic"
)
@OpenAPIDefinition(info = @Info(title = "Expert Industries", version = "v1"))
public class SwaggerConfig {

//swagger link to open the api list = http://localhost:8080/swagger-ui/index.html
}
