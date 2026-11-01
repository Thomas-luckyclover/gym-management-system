package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.TransactionHistoryDAO;
import com.gms.dto.ServiceResult;
import com.gms.dto.TransactionHistoryItem;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public class TransactionHistoryService {

    public static final int PAGE_SIZE = 20;

    private static final Set<String> VALID_STATUSES =
            Set.of("Pending", "Success", "Failed", "Expired");

    private final TransactionHistoryDAO historyDAO =
            new TransactionHistoryDAO();

    public ServiceResult<List<TransactionHistoryItem>> getHistory(
            int accountId,
            int page,
            String status) {

        if (accountId <= 0) {
            return ServiceResult.fail(Messages.MSG153_SESSION_INVALID);
        }

        if (page < 1 || page > 100000) {
            return ServiceResult.fail(Messages.MSG175_PAGE_NUMBER_INVALID);
        }

        String filter = status == null ? "" : status.trim();

        if (!filter.isEmpty() && !VALID_STATUSES.contains(filter)) {
            return ServiceResult.fail(Messages.MSG176_STATUS_FILTER_INVALID);
        }

        int offset = (page - 1) * PAGE_SIZE;

        try (Connection conn = DBContext.getConnection()) {
            List<TransactionHistoryItem> items =
                    historyDAO.findByAccount(
                            conn,
                            accountId,
                            filter.isEmpty() ? null : filter,
                            offset,
                            PAGE_SIZE + 1);

            return ServiceResult.ok(items);

        } catch (SQLException e) {
            System.err.println(
                    "[TransactionHistory] SQLState="
                    + e.getSQLState()
                    + ", errorCode=" + e.getErrorCode());

            return ServiceResult.fail(
                    Messages.MSG177_TRANSACTION_HISTORY_LOAD_FAILED);
        }
    }
}