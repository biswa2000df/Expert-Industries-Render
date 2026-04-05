package expert.industries.render;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(RenderApplication.class, args);
    }
}
