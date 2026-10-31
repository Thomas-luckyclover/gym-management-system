package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.FeedbackDAO;
import com.gms.dto.ServiceResult;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.function.Supplier;

/** Kiểm tra và lưu góp ý, chấm lại PT theo tháng VN (CBR-116). */
public class FeedbackService {
    private final FeedbackDAO dao;
    private final Supplier<LocalDate> today;

    public FeedbackService() {
        this(new FeedbackDAO(), DateTimeUtil::today);
    }

    public FeedbackService(FeedbackDAO dao, Supplier<LocalDate> today) {
        this.dao = dao;
        this.today = today;
    }

    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection();
    }

    /** Lưu góp ý thường hoặc upsert đánh giá trong tháng hiện tại. */
    public ServiceResult<Void> save(int customerId, Integer ptId, Integer rating, String content) {
        // B1: kiểm tra PT, số sao và nội dung (DBR-66)
        if (ptId != null && (rating == null || rating < 1 || rating > 5)) {
            return ServiceResult.failField("rating", String.format(Messages.MSG21_SELECT_REQUIRED, "rating"));
        }
        if (ptId == null && rating != null) {
            return ServiceResult.failField("ratedPtEmployeeId", String.format(Messages.MSG21_SELECT_REQUIRED, "PT"));
        }
        String trimmed = content == null ? "" : content.strip();
        if (trimmed.length() < 10 || trimmed.length() > 1000) {
            return ServiceResult.failField("content", Messages.MSG312_FEEDBACK_CONTENT_LENGTH);
        }
        LocalDate month = today.get().withDayOfMonth(1);
        // B2: kiểm tra quyền chấm và lưu phiếu (CBR-116, DBR-67)
        for (int attempt = 0; attempt < 2; attempt++) {
            try (Connection conn = openConnection()) {
                conn.setAutoCommit(false);
                try {
                    if (ptId != null && !dao.canRate(conn, customerId, ptId, month)) {
                        conn.rollback();
                        return ServiceResult.fail(Messages.MSG106_PT_RATING_NOT_ELIGIBLE);
                    }
                    boolean updated = ptId != null && dao.updateRating(conn, customerId, ptId, rating, month, trimmed);
                    if (!updated) {
                        dao.insert(conn, customerId, ptId, rating, month, trimmed);
                    }
                    conn.commit();
                    return ServiceResult.ok(null, updated ? Messages.MSG107_PT_RATING_UPDATED : Messages.MSG23_SAVED);
                } catch (SQLException e) {
                    conn.rollback();
                    if (ptId != null && attempt == 0 && isDuplicate(e)) {
                        continue;
                    }
                    throw e;
                } catch (RuntimeException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            }
        }
        return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
    }

    private boolean isDuplicate(SQLException error) {
        for (SQLException e = error; e != null; e = e.getNextException()) {
            if (e.getErrorCode() == 2601 || e.getErrorCode() == 2627) {
                return true;
            }
        }
        return false;
    }
}
