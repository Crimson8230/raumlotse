package at.mci.igp.raumlotse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"AUTH_ATTEMPT_HMAC_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
		"SPRING_DATASOURCE_PASSWORD=test-only"
})
class RaumlotseApplicationTests {

	@Test
	void contextLoads() {
	}

}
