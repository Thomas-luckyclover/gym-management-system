package com.gms.service;

import com.gms.dto.PtRatingView;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kiểm tra ngưỡng riêng tư và sáu mức lời nhắn của điểm PT. */
class PtRatingServiceTest {

    private final PtDashboardService service = new PtDashboardService();

    @Test
    void hidesScoreWhenFewerCustomersThanP22() {
        PtRatingView rating = new PtRatingView(2, 10);

        service.prepareRating(rating, 3);

        assertFalse(rating.isRatingVisible());
        assertNull(rating.getAverageRating());
        assertEquals("Chưa đủ đánh giá để hiện điểm tháng này.", rating.getMessage());
    }

    @Test
    void zeroRatingsShowsEmptyState() {
        PtRatingView rating = new PtRatingView(0, 0);

        service.prepareRating(rating, 3);

        assertFalse(rating.isRatingVisible());
        assertNull(rating.getAverageRating());
        assertEquals("Chưa có đánh giá trong tháng này.", rating.getMessage());
    }

    @Test
    void choosesSixMessagesAtTheRatingBoundaries() {
        long[] totalsForThreeCustomers = {3, 4, 7, 10, 13, 15};
        String[] expectedParts = {
            "Điểm chưa như mong muốn", "Hơi hụt nhịp", "Đường tiến bộ",
            "Đang vào guồng rồi", "Điểm rất tốt", "5 sao trọn vẹn"
        };

        for (int i = 0; i < totalsForThreeCustomers.length; i++) {
            PtRatingView rating = new PtRatingView(3, totalsForThreeCustomers[i]);
            service.prepareRating(rating, 3);
            assertTrue(rating.isRatingVisible());
            assertTrue(rating.getMessage().contains(expectedParts[i]));
        }
    }

    @Test
    void nearFiveDoesNotBecomePerfectFive() {
        PtRatingView rating = new PtRatingView(100, 499);

        service.prepareRating(rating, 3);

        assertEquals(new BigDecimal("4.99"), rating.getAverageRating());
        assertTrue(rating.getMessage().contains("Điểm rất tốt"));
    }

    @Test
    void invalidP22IsRejected() {
        assertThrows(IllegalStateException.class,
                () -> service.prepareRating(new PtRatingView(3, 15), 0));
    }
}
