package com.gms.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import java.io.IOException;

/**
 * Ép UTF-8 cho mọi request/response để form gửi tiếng Việt có dấu không bị lỗi font ("Nguyá»…n").
 *
 * Vì sao không cần lo thứ tự chạy giữa các Filter (@WebFilter không đảm bảo thứ tự):
 * encoding chỉ cần được đặt TRƯỚC lần đọc tham số đầu tiên, mà không Filter nào của dự án gọi getParameter()
 * — chỉ Servlet đọc, và Servlet luôn chạy sau tất cả Filter. Vì vậy quy ước: Filter KHÔNG được gọi getParameter().
 */
@WebFilter("/*")
public class CharacterEncodingFilter implements Filter {

    private static final String ENCODING = "UTF-8";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding(ENCODING);
        response.setCharacterEncoding(ENCODING);
        chain.doFilter(request, response);
    }
}
