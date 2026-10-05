package at.mci.igp.raumlotse;

import at.mci.igp.raumlotse.config.BookingConfirmationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(BookingConfirmationProperties.class)
public class RaumlotseApplication {

	public static void main(String[] args) {
		SpringApplication.run(RaumlotseApplication.class, args);
	}

}
