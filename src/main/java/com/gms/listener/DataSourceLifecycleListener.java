package com.gms.listener;

import com.gms.util.DBContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Đóng connection pool của DBContext khi app dừng / redeploy.
 *
 * Không đóng thì mỗi lần NetBeans "Deploy on Save" các kết nối cũ vẫn treo trên SQL Server
 * cho tới khi Tomcat tắt hẳn.
 */
@WebListener
public class DataSourceLifecycleListener implements ServletContextListener {

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        DBContext.closePool();
    }
}
