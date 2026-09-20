package at.mci.igp.raumlotse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RaumlotseApplication {

	public static void main(String[] args) {
		SpringApplication.run(RaumlotseApplication.class, args);
	}

}
