package com.saga.be;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
class BeApplicationTests {

	@Test
	void contextLoads() {
	}

}
