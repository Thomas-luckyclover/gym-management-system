/**
 * Filter (@WebFilter, cấm khai báo trong web.xml): CharacterEncodingFilter (UTF-8), AuthFilter (đã đăng nhập chưa),
 * RoleAreaFilter + 5 Filter con theo khu vực URL (đúng vai trò chưa). Quy ước: Filter không gọi getParameter().
 */
package com.gms.filter;
