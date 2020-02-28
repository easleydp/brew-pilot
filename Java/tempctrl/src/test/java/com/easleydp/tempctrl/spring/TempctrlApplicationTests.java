package com.easleydp.tempctrl.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "pwdhash.guest: $2y$04$ZuztduDisdh4KBxSREaxCOrbzRxYuSweQxseZ7fPMa5v3us1aolMa", // "guest"
        "pwdhash.admin: $2y$04$iwY4S/mX48AaoAUG8.8hieVJJJkKLWvoQoReOeXqdAZu7GCYe5EIi"  // "admin"
})
class TempctrlApplicationTests {

	@Test
	void contextLoads() {
	}

}
