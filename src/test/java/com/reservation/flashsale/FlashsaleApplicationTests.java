package com.reservation.flashsale;

import org.junit.jupiter.api.Test;
import com.reservation.flashsale.stock.service.StockInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class FlashsaleApplicationTests {

	@MockitoBean
	private StockInitializer stockInitializer;

	@Test
	void contextLoads() {
	}

}
