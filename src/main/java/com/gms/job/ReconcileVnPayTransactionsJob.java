package com.gms.job;

import com.gms.service.PaymentReconciliationService;
import java.sql.SQLException;

/** Job chỉ điều phối, Service thực hiện truy vấn và nghiệp vụ. */
public class ReconcileVnPayTransactionsJob implements BackgroundJob {

    private final PaymentReconciliationService service =
            new PaymentReconciliationService();

    @Override
    public String getName() {
        return "Reconcile VNPay transactions";
    }

    @Override
    public void run() throws SQLException {
        service.runOnce();
    }
}