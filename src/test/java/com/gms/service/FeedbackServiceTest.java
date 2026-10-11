package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.FeedbackDAO;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra Rating, quyền chấm và upsert đánh giá theo tháng VN. */
class FeedbackServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026,10,1);
    private static final String CONTENT = "Nội dung đánh giá đủ dài.";

    private static class FakeDAO extends FeedbackDAO {
        boolean eligible = true;
        int calls;
        int updates;
        int duplicateCode;
        LocalDate month;
        Map<String,Boolean> read = new HashMap<>();

        private String key(int customer, int pt, LocalDate month) {
            return customer+":"+pt+":"+month;
        }

        @Override public boolean canRate(Connection c,int customer,int pt,LocalDate month) {
            calls++;
            this.month=month;
            return eligible;
        }

        @Override public boolean updateRating(Connection c,int customer,int pt,int rating,LocalDate month,String content) {
            String key=key(customer,pt,month);
            if (!read.containsKey(key)) {
                return false;
            }
            read.put(key,false);
            updates++;
            return true;
        }

        @Override public void insert(Connection c,int customer,Integer pt,Integer rating,LocalDate month,String content)
                throws SQLException {
            calls++;
            read.put(key(customer,pt == null ? 0 : pt,month),false);
            if (duplicateCode!=0) {
                int code=duplicateCode;
                duplicateCode=0;
                throw new SQLException("duplicate","23000",code);
            }
        }
    }

    private FeedbackService service(FakeDAO dao) {
        return new FeedbackService(dao,() -> TODAY) {
            @Override protected Connection openConnection() {
                return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},
                        (p,m,a) -> m.getReturnType()==boolean.class ? false : null);
            }
        };
    }

    @Test void ptWithoutRatingRejectedWithMsg21() {
        FakeDAO dao=new FakeDAO();
        assertEquals(String.format(Messages.MSG21_SELECT_REQUIRED,"rating"),service(dao).save(1,5,null,CONTENT).getMessage());
        assertEquals(0,dao.calls);
    }

    @Test void ratingWithoutPtRejectedWithMsg21() {
        FakeDAO dao=new FakeDAO();
        assertEquals(String.format(Messages.MSG21_SELECT_REQUIRED,"PT"),service(dao).save(1,null,4,CONTENT).getMessage());
        assertEquals(0,dao.calls);
    }

    @Test void zeroRejected() {
        FakeDAO dao=new FakeDAO();
        assertFalse(service(dao).save(1,5,0,CONTENT).isSuccess());
        assertEquals(0,dao.calls);
    }

    @Test void sixRejected() {
        FakeDAO dao=new FakeDAO();
        assertFalse(service(dao).save(1,5,6,CONTENT).isSuccess());
        assertEquals(0,dao.calls);
    }

    @Test void ineligiblePtRejectedWithMsg106() {
        FakeDAO dao=new FakeDAO();
        dao.eligible=false;
        assertEquals(Messages.MSG106_PT_RATING_NOT_ELIGIBLE,service(dao).save(1,5,4,CONTENT).getMessage());
        assertTrue(dao.read.isEmpty());
    }

    @Test void reratingUpdatesAndResetsUnread() {
        FakeDAO dao=new FakeDAO();
        FeedbackService service=service(dao);
        assertTrue(service.save(1,5,4,CONTENT).isSuccess());
        dao.read.replaceAll((k,v) -> true);
        assertEquals(Messages.MSG107_PT_RATING_UPDATED,service.save(1,5,2,CONTENT).getMessage());
        assertEquals(1,dao.read.size());
        assertFalse(dao.read.values().iterator().next());
        assertEquals(TODAY.withDayOfMonth(1),dao.month);
    }

    @Test void twoCustomersCreateTwoRows() {
        FakeDAO dao=new FakeDAO();
        FeedbackService service=service(dao);
        assertTrue(service.save(1,5,4,CONTENT).isSuccess());
        assertTrue(service.save(2,5,3,CONTENT).isSuccess());
        assertEquals(2,dao.read.size());
    }

    @Test void duplicate2601RetriesAsUpdate() {
        assertDuplicate(2601);
    }

    @Test void duplicate2627RetriesAsUpdate() {
        assertDuplicate(2627);
    }

    private void assertDuplicate(int code) {
        FakeDAO dao=new FakeDAO();
        dao.duplicateCode=code;
        assertEquals(Messages.MSG107_PT_RATING_UPDATED,service(dao).save(1,5,4,CONTENT).getMessage());
        assertEquals(1,dao.read.size());
        assertEquals(1,dao.updates);
    }

    @Test void ordinaryFeedbackAllowedWithoutRating() {
        assertTrue(service(new FakeDAO()).save(1,null,null,CONTENT).isSuccess());
    }
}
