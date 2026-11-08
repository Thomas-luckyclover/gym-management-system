package com.gms.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import com.gms.util.RequestUtil;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Filter ép encoding UTF-8 cho mọi request/response (mapping /*) để form tiếng Việt có dấu không bị lỗi font.
 *
 * <p>Với tài nguyên tĩnh (css/js/images) thì KHÔNG set charset response, tránh Tomcat đọc lại file theo bảng mã máy.</p>
 */
@WebFilter("/*")
public class CharacterEncodingFilter implements Filter {

    private static final String ENCODING = "UTF-8";

    private static final String[] STATIC_PATH_PREFIXES = {"/css/", "/js/", "/images/"};

    /**
     * Đặt encoding request là UTF-8, đặt encoding response là UTF-8 (trừ file tĩnh) rồi cho request đi tiếp.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding(ENCODING);

        // File tĩnh: KHÔNG đặt charset cho response. Nếu đặt, Tomcat DefaultServlet sẽ đọc file theo bảng mã
        // mặc định của máy (Cp1252 trên Windows + JDK 17) rồi mã hóa lại sang UTF-8 → chữ Việt trong .js bị vỡ
        // ("là" thành "lÄ‚ "). Không đặt thì Tomcat gửi nguyên byte của file (vốn đã là UTF-8).
        if (!isStaticResource((HttpServletRequest) request)) {
            response.setCharacterEncoding(ENCODING);
        }
        chain.doFilter(request, response);
    }

    private boolean isStaticResource(HttpServletRequest request) {
        String path = RequestUtil.getPathWithinApp(request);
        for (String prefix : STATIC_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
