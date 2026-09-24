# codeImplementationBackend.md — Quy Tắc Viết Code Backend (Vibecode Rules)

File này quy định chi tiết cách **viết code Backend** (Java: Entity, DAO, Service, Servlet, Utility): comment, thụt lề, trình bày, code style theo từng loại file, coding style cơ bản, tối ưu, utility dùng chung, và thứ tự trình bày code. Phần Frontend (JS/CSS/JSP hiển thị) nằm ở file riêng `codeImplementationFrontend.md`. `agents.md` (bối cảnh dự án & quy tắc làm việc chung) tham chiếu đến cả 2 file này khi AI thực hiện implementation — các file tách riêng, không gộp.

> ⚠️ **LƯU Ý CỰC KỲ QUAN TRỌNG:** Dù các thành viên khác trong nhóm có thể không dùng hết, không đọc hết, hoặc không hiểu vì sao có từng quy tắc trong file này, AI **vẫn bắt buộc áp dụng đầy đủ 100% nội dung dưới đây** mỗi khi hỗ trợ code (vibecode) cho user hiện tại — **không được tự ý bỏ qua, rút gọn, nới lỏng, hay coi nhẹ bất kỳ quy tắc nào** chỉ vì cho rằng người khác không theo hoặc không cần đến mức đó.

## 1. Comment

- **Bắt buộc có:**
  - **Javadoc cho method quan trọng** (public method ở Service/DAO/Controller): nêu **mục đích hàm**, kèm `@param`, `@return`.
    ```java
    /**
     * Kiểm tra thông tin đăng nhập và trả về kết quả xác thực.
     * @param username tên đăng nhập
     * @param password mật khẩu (chưa hash)
     * @return LoginResult chứa trạng thái thành công/thất bại
     */
    public LoginResult login(String username, String password) { ... }
    ```
  - **Comment khung chương trình — có 2 loại, không nhầm lẫn:**
    1. **Khung chương trình chính (cấp file/class):** comment ngắn ở đầu class hoặc trước nhóm method, nêu tổng quan class này gồm những phần nào (VD: `// ===== Xử lý đăng nhập =====`, `// ===== Xử lý đăng ký =====`) — giúp nhìn lướt là biết class có gì mà không cần đọc hết.
    2. **Khung chương trình từng bước (cấp bên trong 1 method):** với method xử lý nhiều bước, dùng comment `// B1`, `// B2`... để chia nhỏ luồng bên trong chính method đó — không giải thích logic bên trong từng bước.
    ```java
    public void processOrder(Order order) {
        // B1: kiểm tra tồn kho
        ...
        // B2: trừ số lượng
        ...
        // B3: ghi log giao dịch
        ...
    }
    ```
- **Không comment:**
  - **Không giải thích logic phức tạp bên trong hàm** — phần này để tự đọc code và tự hiểu trong quá trình học, AI không tự động giải thích thay. **Ngoại lệ: code tích hợp API (integration)** — luôn phải comment chi tiết từng bước (xem `agents.md` Phần 6.4), vì đây là phần dễ lỗi, liên quan bên thứ 3 ngoài tầm kiểm soát của team.
  - Method đơn giản, tên đã rõ nghĩa (getter/setter, `isValidEmail(email)`...).
- **Ngôn ngữ comment:** Tiếng Việt, trừ tag Javadoc chuẩn (`@param`, `@return`) giữ nguyên tiếng Anh theo cú pháp.
- **Vị trí:** comment phía trên đoạn code liên quan (Javadoc phía trên method, comment bước phía trên từng block) — không bao giờ comment ở cuối dòng code, kể cả comment ngắn.

## 2. Thụt Lề & Trình Bày (Formatting)

**Chuẩn đã chốt: K&R style, thụt lề 4 spaces** (ngoặc `{` mở cùng dòng, không dùng tab).

```java
public void checkLogin(String user) {
    if (isValid(user)) {
        login(user);
    }
}
```

| Ngôn ngữ | Thụt lề | Dấu ngoặc `{}` |
|---|---|---|
| Java | 4 spaces | K&R (mở ngoặc cùng dòng) |
| JSP | 4 spaces cho block `<% %>`, 2 spaces cho HTML/JSP tag thường | theo Java bên trong scriptlet |
| JavaScript | 4 spaces | K&R (mở ngoặc cùng dòng) |
| CSS | 2 spaces | mỗi thuộc tính 1 dòng |
| SQL | mệnh đề (`SELECT`, `FROM`, `WHERE`, `JOIN`) xuống dòng riêng, viết hoa keyword | — |

- Luôn có 1 dòng trống giữa các method/function.
- File kết thúc bằng 1 dòng trống.
- Không để nhiều hơn 1 dòng trống liên tiếp giữa các block.

## 3. Code Style Theo Từng Loại

| Loại code | Style áp dụng |
|---|---|
| Java (Model/Service/DAO) | PascalCase class, camelCase method/biến, 1 class 1 file. |
| Servlet (Controller) | Tách rõ `doGet`/`doPost`, mỗi method Controller chỉ điều phối — không nhồi business logic vào Servlet. Mapping URL bằng `@WebServlet("/duong-dan")` ngay trên class — **cấm tạo `web.xml` để khai báo servlet-mapping** (ngoại lệ error page, xem chi tiết bên dưới). |
| JSP (View) | Hạn chế tối đa scriptlet `<% %>`, ưu tiên JSTL (`<c:if>`, `<c:forEach>`) nếu team đã dùng. |
| SQL Server | Viết hoa từ khóa, tên bảng/cột theo `NamingConvention.md`, luôn dùng parameterized query (PreparedStatement) — không nối chuỗi SQL trực tiếp (tránh SQL Injection). |
| JavaScript | Ưu tiên `const`/`let`, tránh `var`; function nhỏ, đơn nhiệm. |
| CSS | Ưu tiên class hơn ID để style; đặt tên theo `NamingConvention.md` (kebab-case). |

**Màn hình đặc biệt (AJAX / REST API / kiến trúc khác):** một số màn hình không đi theo luồng JSP-Servlet thuần (VD: gọi AJAX để load dữ liệu không reload trang, hoặc có REST API riêng trả JSON). Với các màn hình này, **vẫn áp dụng toàn bộ quy tắc trong file này và `agents.md`** (MVC phân lớp, naming, comment, format, validation qua `util`...) — chỉ khác ở cách Controller trả dữ liệu (JSON thay vì forward JSP). Bám sát nhất có thể với chuẩn chung, không tự do phá cấu trúc chỉ vì cơ chế gọi khác.

**Lưu ý siêu quan trọng — cấm dùng `web.xml` để mapping Servlet/Filter:** mọi Servlet bắt buộc mapping URL bằng annotation `@WebServlet` ngay trên class, không tạo/khai báo `web.xml` (deployment descriptor) để mapping servlet dưới bất kỳ hình thức nào.
```java
@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    ...
}
```

**Ngoại lệ duy nhất — `web.xml` cho Error Page (404/500):** Servlet API **không có annotation nào** khai báo `<error-page>` theo mã lỗi HTTP — đây là giới hạn kỹ thuật thật, không phải cách làm tùy chọn. `<error-page>` không phải URL route do bạn định nghĩa (khác bản chất với servlet-mapping), mà là cấu hình cho container biết forward đi đâu khi chính container trả lỗi (404 — không tìm thấy URL, 500 — lỗi không bắt được ở tầng code) — nên dùng `web.xml` riêng cho việc này **không vi phạm tinh thần quy tắc gốc** (tránh mapping URL rải rác 2 nơi). Cho phép tạo `web.xml` **chỉ với nội dung `<error-page>`**, tuyệt đối không thêm `<servlet>`/`<servlet-mapping>`/`<filter-mapping>` vào file này.

```xml
<!-- web.xml — CHỈ dùng cho error page, không dùng cho servlet/filter mapping -->
<web-app ...>
    <error-page>
        <error-code>404</error-code>
        <location>/WEB-INF/jsp/error/404.jsp</location>
    </error-page>
    <error-page>
        <error-code>500</error-code>
        <location>/WEB-INF/jsp/error/500.jsp</location>
    </error-page>
</web-app>
```

**Vì sao dự án cần có 404/500:** tránh lộ stack trace kỹ thuật (bảo mật — không để lộ đường dẫn file, tên class, câu SQL lỗi ra màn hình) và tránh hiện trang lỗi mặc định xấu của Tomcat lúc demo. Tối thiểu cần 404 + 500; không bắt buộc làm đủ 403 nếu không có thời gian.

## 4. Coding Style Cơ Bản

- **1 dòng chỉ chứa 1 logic** — tuyệt đối không gộp nhiều thao tác/điều kiện vào chung 1 dòng.

  **Không làm (nhiều logic trên 1 dòng):**
  ```java
  if (user != null && user.isActive() && user.getRole().equals("ADMIN")) doAdminTask(user);
  ```

  **Nên làm (tách rõ từng logic):**
  ```java
  boolean isValidUser = user != null && user.isActive();
  boolean isAdmin = isValidUser && user.getRole().equals("ADMIN");

  if (isAdmin) {
      doAdminTask(user);
  }
  ```

- Không dùng toán tử ba ngôi (`? :`) lồng nhau hoặc gán nhiều biến trên 1 dòng bằng dấu `;`.
- Mỗi khai báo biến (`declare`) một dòng riêng, không khai báo nhiều biến chung 1 dòng kiểu `int a = 1, b = 2;`.
- Điều kiện phức tạp nên tách thành biến trung gian có tên rõ nghĩa (như ví dụ trên) thay vì viết `if` dài dòng khó đọc.

## 5. Tối Ưu Code

- **Chỉ tối ưu phần code đơn giản và thật sự cần thiết** — không đụng đến tối ưu database (không tự ý sửa index, viết lại schema, hay thay đổi cách query ở tầng DB nếu không được yêu cầu).
- Phạm vi tối ưu cho phép: gộp biến lặp lại không cần thiết, bỏ vòng lặp/điều kiện dư thừa, tránh gọi lại 1 hàm nhiều lần với cùng input trong cùng 1 method.
- **Nguyên tắc quan trọng nhất: nếu tối ưu khiến code trở nên loằng ngoằng, khó đọc hơn — bỏ qua, ưu tiên giữ code dễ hiểu.** Không đánh đổi độ rõ ràng lấy vài dòng code ngắn hơn.
- Không tự ý tối ưu performance ở tầng database (query, index, transaction) trừ khi user yêu cầu rõ.

## 6. Utility Dùng Chung (Validation, Password)

Đặt trong package `util`, tách riêng khỏi các layer chính:

```
src/
 └── com/project/
      ├── entity/
      ├── dao/
      ├── service/
      ├── servlet/
      └── util/
           ├── ValidationUtil.java
           └── PasswordUtil.java   (BCrypt hash/check)
```

- **Chỉ gọi từ Service**, không gọi trong DAO (DAO chỉ lo truy vấn), hạn chế gọi trực tiếp trong Servlet (validate tập trung ở Service để business logic không rải rác).
- Hàm validate phải **thuần (pure function)**: chỉ nhận input, trả `true/false` (hoặc giá trị đã parse) — **không** đọc input (không `Scanner`), **không** tự in lỗi (không `System.out`/`System.err`), **không** vòng lặp hỏi lại như code console. Web không "chặn đứng chờ nhập lại" được — lỗi phải trả về để Servlet/JSP tự xử lý hiển thị.

**Ví dụ `ValidationUtil`:**
```java
public class ValidationUtil {

    private static final String EMAIL_REGEX = "^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$";

    /**
     * Kiểm tra chuỗi có null hoặc rỗng không.
     * @param value chuỗi cần kiểm tra
     * @return true nếu null hoặc rỗng
     */
    public static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Kiểm tra định dạng email hợp lệ.
     * @param email chuỗi email cần kiểm tra
     * @return true nếu đúng định dạng
     */
    public static boolean isValidEmail(String email) {
        if (isNullOrEmpty(email)) {
            return false;
        }
        return email.matches(EMAIL_REGEX);
    }

    /**
     * Kiểm tra chuỗi có phải số nguyên hợp lệ không.
     * @param value chuỗi cần kiểm tra
     * @return true nếu parse được thành số nguyên
     */
    public static boolean isValidInteger(String value) {
        if (isNullOrEmpty(value)) {
            return false;
        }
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Kiểm tra độ dài chuỗi trong khoảng cho phép.
     * @param value chuỗi cần kiểm tra
     * @param min độ dài tối thiểu
     * @param max độ dài tối đa
     * @return true nếu độ dài hợp lệ
     */
    public static boolean isLengthValid(String value, int min, int max) {
        if (isNullOrEmpty(value)) {
            return false;
        }
        int length = value.trim().length();
        return length >= min && length <= max;
    }
}
```

**Dùng trong Service:**
```java
public ServiceResult<User> register(String username, String email, String rawPassword) {
    // B1: validate input
    if (!ValidationUtil.isLengthValid(username, 3, 50)) {
        return ServiceResult.fail("Username phải từ 3-50 ký tự");
    }
    if (!ValidationUtil.isValidEmail(email)) {
        return ServiceResult.fail("Email không hợp lệ");
    }
    if (!ValidationUtil.isLengthValid(rawPassword, 6, 100)) {
        return ServiceResult.fail("Mật khẩu phải từ 6 ký tự trở lên");
    }

    // B2: xử lý nghiệp vụ tiếp theo
    ...
}
```

## 7. Entity — Setter (Validate Ở Đâu?)

Setter trong Entity **không validate, không tự revert, không print lỗi** — khác hẳn kiểu code console. Lý do: setter có thể bị gọi từ DAO khi map dữ liệu (không liên quan request cụ thể để hiển thị lỗi), và việc âm thầm giữ nguyên giá trị cũ khi sai sẽ khiến user tưởng đã lưu thành công dù thực ra chưa.

| | Console (kiểu cũ) | Production (chuẩn áp dụng) |
|---|---|---|
| Setter có validate không | Có | **Không** — setter chỉ gán |
| Validate ở đâu | Ngay trong setter | Tầng **Service**, qua `ValidationUtil` |
| Sai thì xử lý sao | Tự revert giá trị cũ, print ra console | Trả `ServiceResult.fail(message)` rõ ràng, Servlet/JSP hiển thị cho user |
| Field bất biến (ID, ngày tạo...) | `final`, không viết setter | Giữ y nguyên cách làm này |

**Entity — setter thuần, không validate:**
```java
public void setMath(double math) {
    this.math = math;
}
```

**Service — nơi validate thật sự, trả lỗi rõ ràng thay vì im lặng revert:**
```java
public ServiceResult<Student> updateMathScore(Student student, double math) {
    if (!ValidationUtil.isInRange(math, 0, 10)) {
        return ServiceResult.fail("Điểm Toán phải từ 0 đến 10, giá trị không hợp lệ đã bị từ chối");
    }
    student.setMath(math);
    return ServiceResult.ok(student);
}
```

**Field bất biến (VD: ID auto-increment):** không viết setter, khai báo `final` — loại bỏ hẳn khả năng thay đổi sau khi tạo, thay vì validate trong setter.
```java
private final String id;   // không setter
```

## 8. Nguyên Tắc Tối Quan Trọng: Code Phải Viết Từ Trên Xuống Dưới

**Đây là lưu ý siêu quan trọng, áp dụng cho mọi file (Java, JS, JSP, CSS...):**

- Code trong 1 file phải viết theo **đúng thứ tự logic từ trên xuống dưới** — đọc từ đầu file đến cuối là hiểu được luồng chạy, không phải nhảy qua nhảy lại.
- **Duy nhất ngoại lệ:** hằng số (`constant`/`static final` trong Java, `const`/biến toàn cục trong JS) được đặt ở **đầu file/đầu class**.
- **Tuyệt đối không** gom các đoạn code "cùng loại logic" nằm rải rác trong file rồi dồn chúng lại viết cạnh nhau (VD: gom hết tất cả các hàm validate lại 1 chỗ, tất cả các hàm tính toán lại 1 chỗ...) nếu điều đó phá vỡ thứ tự chạy tự nhiên của chương trình.
- **Lý do:** ưu tiên cao nhất là **đọc từ trên xuống dễ hiểu, dễ debug bằng mắt khi review code** — không tối ưu cấu trúc file theo kiểu phân loại logic trừu tượng nếu đánh đổi lấy việc phải nhảy lung tung mới hiểu được luồng chạy.

## 9. Quản Lý Dependency (`pom.xml`)

`pom.xml` là file Maven khai báo **toàn bộ thư viện (dependency)** dự án dùng, cùng thông tin build. Vì đây là phần dễ rối nhất với người mới, giải thích kỹ từng phần:

### 9.1. Cấu trúc cơ bản cần có

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.project</groupId>
    <artifactId>student-management</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>war</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- Khai báo từng thư viện ở đây -->
    </dependencies>
</project>
```

- **`groupId`/`artifactId`/`version`:** định danh dự án — đặt 1 lần lúc khởi tạo, gần như không đổi sau đó.
- **`packaging`:** dự án JSP/Servlet luôn là `war` (Web Application Archive), không phải `jar`.
- **`properties`:** khai báo phiên bản Java (`maven.compiler.source`/`target`) và encoding `UTF-8` (bắt buộc để không lỗi font tiếng Việt) — đặt ở đây 1 lần, dùng chung cho cả project.

### 9.2. Dependency cần có cho stack JSP/Servlet + SQL Server + BCrypt

```xml
<dependencies>
    <!-- Servlet API -->
    <dependency>
        <groupId>jakarta.servlet</groupId>
        <artifactId>jakarta.servlet-api</artifactId>
        <version>6.0.0</version>
        <scope>provided</scope>
    </dependency>

    <!-- JSTL cho JSP (c:if, c:forEach...) -->
    <dependency>
        <groupId>jakarta.servlet.jsp.jstl</groupId>
        <artifactId>jakarta.servlet.jsp.jstl-api</artifactId>
        <version>3.0.0</version>
    </dependency>
    <dependency>
        <groupId>org.glassfish.web</groupId>
        <artifactId>jakarta.servlet.jsp.jstl</artifactId>
        <version>3.0.1</version>
    </dependency>

    <!-- Driver kết nối SQL Server -->
    <dependency>
        <groupId>com.microsoft.sqlserver</groupId>
        <artifactId>mssql-jdbc</artifactId>
        <version>12.6.1.jre11</version>
    </dependency>

    <!-- BCrypt hash password -->
    <dependency>
        <groupId>org.mindrot</groupId>
        <artifactId>jbcrypt</artifactId>
        <version>0.4</version>
    </dependency>
</dependencies>
```

- **`scope: provided`** ở Servlet API: nghĩa là thư viện này **có sẵn ở server lúc chạy** (Tomcat đã có), Maven chỉ cần nó lúc build/compile — không đóng gói kèm vào file `.war`. Nếu thiếu `scope` này, dễ bị lỗi xung đột class lúc deploy.
- Version cụ thể ở trên là ví dụ tại thời điểm hiện tại — nên kiểm tra phiên bản mới nhất tương thích với JDK/Tomcat của bạn khi thêm thật.

### 9.3. Nguyên tắc khi sửa `pom.xml`

- **Chỉ thêm dependency thật sự cần dùng** — không thêm "phòng khi cần" hay copy nguyên `pom.xml` từ project khác rồi giữ dependency thừa không dùng tới.
- **Luôn ghi version cụ thể** (VD: `12.6.1.jre11`), **không dùng `LATEST`/`RELEASE`** — vì bản mới có thể đổi API, gây lỗi bất ngờ giữa các lần build khác thời điểm.
- Khi thêm 1 dependency mới, phải build thử lại (`mvn clean install` hoặc build trong IDE) để chắc chắn không xung đột version với dependency đã có.
- Không tự ý đổi `maven.compiler.source`/`target` (phiên bản Java) nếu không được yêu cầu — ảnh hưởng toàn bộ project compile được hay không.
- Nếu SRS/SDS có yêu cầu thư viện cụ thể (VD: 1 thư viện export Excel, PDF...), tham chiếu đúng theo Phần 4 (mức ưu tiên nguồn) của `agents.md` trước khi tự chọn thư viện khác.

## 10. Exception Handling

- **DAO:** không tự `catch` — `throw SQLException` lên trên, để Service quyết định xử lý.
  ```java
  // DAO — không catch, throw thẳng lên
  public User findByUsername(String username) throws SQLException {
      ...
      if (rs.next()) {
          return mapResultSetToUser(rs);
      }
      return null; // null ở đây CHỈ nghĩa "không tìm thấy" — không lẫn với lỗi kỹ thuật
  }
  ```
  **Vì sao:** nếu DAO tự `catch` rồi trả `null` khi lỗi, `null` sẽ mang 2 nghĩa lẫn lộn — "không tìm thấy" (đúng nghiệp vụ) và "lỗi kết nối DB" (bất thường). Service không phân biệt được, dễ báo sai nguyên nhân cho user (VD: báo "sai username" trong khi thực ra do mất kết nối DB). DAO cũng không đủ ngữ cảnh nghiệp vụ để quyết định nên xử lý lỗi ra sao — đó là việc của Service, giống nguyên tắc "DAO chỉ truy vấn, Service mới xử lý nghiệp vụ" đã chốt từ Phần 1.
- **Service:** bắt exception cụ thể (`SQLException`), **không bắt `Exception` chung chung** (dễ nuốt luôn lỗi lập trình không liên quan). Trả `ServiceResult.fail(message)` với message thân thiện, tiếng Việt, **không lộ chi tiết kỹ thuật** (không hiện nguyên `e.getMessage()` từ SQL Server ra cho user).
- **Log lỗi thật:** `e.printStackTrace()` ngay tại chỗ `catch` trong Service — để dev xem log debug trong console, tách biệt hoàn toàn với message hiển thị cho user.
  ```java
  public ServiceResult<User> login(String username, String password) {
      try {
          User user = userDAO.findByUsername(username);
          if (user == null) {
              return ServiceResult.fail("Tài khoản không tồn tại");
          }
          ...
      } catch (SQLException e) {
          e.printStackTrace(); // log lỗi thật cho dev
          return ServiceResult.fail("Có lỗi hệ thống, vui lòng thử lại sau"); // message an toàn cho user
      }
  }
  ```
- **Servlet:** không tự try-catch riêng — Service đã xử lý xong, Servlet chỉ check `result.isSuccess()`.

## 11. Session & Cookie (Bắt Buộc Có)

Session/Cookie là bắt buộc cho mọi màn hình cần đăng nhập — không tùy chọn. Mô hình chuẩn: **session ngắn hạn (bắt buộc)** cho bảo mật phiên làm việc + **Remember Me (tùy chọn, làm sau)** cho tiện lợi.

### 11.1. Session — bắt buộc

- **Cơ chế:** Servlet/Tomcat tự động dùng cookie tên `JSESSIONID` để gắn user với session trên server — không cần tự viết code tạo cookie thủ công, chỉ cần dùng `HttpSession` là đủ.
- **Chỉ lưu dữ liệu tối thiểu vào session** — không lưu cả object `User` đầy đủ (có `passwordHash`) vào session, tránh rò rỉ nếu session bị lộ:
  ```java
  req.getSession().setAttribute("userId", user.getUserId());
  req.getSession().setAttribute("role", user.getRole());
  ```
- **Thời gian sống (timeout) — chốt 2 tiếng không hoạt động (7200 giây)**, phù hợp cho việc code/demo (tránh văng ra giữa lúc demo trước giảng viên vì 30 phút quá ngắn), đồng thời vẫn có tác dụng bảo vệ nếu quên đăng xuất trên máy dùng chung (phòng lab...) — session tự hết hạn sau 2 tiếng không ai đụng vào. Set ngay lúc login:
  ```java
  req.getSession().setMaxInactiveInterval(7200); // 2 tiếng
  ```
  Nếu SRS có yêu cầu cụ thể khác, ưu tiên theo SRS (Phần 4 mức ưu tiên nguồn của `agents.md`).
- **Logout:** `req.getSession().invalidate();` — hủy sạch toàn bộ session, không tự set từng attribute về `null`.
- **Kiểm tra đăng nhập tập trung bằng `Filter`** (xem Mục 12) — không tự viết `if (session == null)` lặp lại ở từng Servlet.

### 11.2. Remember Me — tùy chọn, làm sau nếu cần

Nếu muốn user không phải đăng nhập lại trong nhiều ngày (kể cả khi server restart) — khác cơ chế session ở trên (session sống trong RAM server, mất khi restart). Cần: 1 cookie riêng chứa token ngẫu nhiên, lưu token đó vào 1 bảng DB riêng gắn với `userId` + thời hạn, check cookie này khi session đã hết để tự đăng nhập lại.

**Chỉ làm phần này nếu SRS yêu cầu "ghi nhớ đăng nhập"/"remember me" — không bắt buộc, không tự ý làm thêm nếu SRS chưa nhắc tới**, vì đây là 1 tính năng riêng cần thêm bảng DB + logic bảo mật token, không phải chỉnh 1 dòng cấu hình.

## 12. Phân Quyền (Authorization) — Dùng Filter

Phân quyền (biết user có quyền làm việc này không) khác với đăng nhập (biết user là ai) — cần tách riêng 2 `Filter`.

### 12.1. Filter kiểm tra đã đăng nhập chưa

```java
@WebFilter("/*")  // áp dụng cho mọi URL, trừ các URL loại trừ bên trong
public class AuthFilter implements Filter {

    // Các URL không cần đăng nhập vẫn được truy cập
    private static final List<String> PUBLIC_PATHS = List.of("/login", "/register", "/css", "/js", "/images");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI().substring(req.getContextPath().length());

        // B1: nếu là URL công khai, cho qua luôn
        boolean isPublicPath = PUBLIC_PATHS.stream().anyMatch(path::startsWith);
        if (isPublicPath) {
            chain.doFilter(request, response);
            return;
        }

        // B2: kiểm tra đã đăng nhập chưa
        Object userId = req.getSession().getAttribute("userId");
        if (userId == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        // B3: đã đăng nhập, cho đi tiếp
        chain.doFilter(request, response);
    }
}
```

### 12.2. Filter kiểm tra quyền theo role

```java
@WebFilter("/admin/*")  // chỉ áp dụng cho các URL bắt đầu bằng /admin/
public class AdminFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String role = (String) req.getSession().getAttribute("role");
        boolean isAdmin = "ADMIN".equals(role);

        if (!isAdmin) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Không có quyền truy cập");
            return;
        }

        chain.doFilter(request, response);
    }
}
```

**Nguyên tắc đặt Filter:**
- `@WebFilter`, giống Servlet — **cấm khai báo qua `web.xml`**, đồng bộ với quy tắc ở Mục 3 (ngoại lệ error page không áp dụng cho Filter, chỉ áp dụng cho `<error-page>`).
- Filter kiểm tra đăng nhập (`AuthFilter`) áp `/*` (toàn site) trừ các path công khai.
- Filter kiểm tra role áp theo URL pattern cụ thể (VD: `/admin/*`) — nếu nhiều nhóm role khác nhau, tạo nhiều Filter riêng theo từng nhóm URL, không gộp chung 1 Filter xử lý nhiều role bằng if-else dài dòng.
- Nếu chỉ 1-2 Servlet lẻ tẻ cần phân quyền riêng biệt (không theo URL pattern rõ ràng) → check ngay đầu `doGet`/`doPost` của Servlet đó, không bắt buộc phải có Filter cho mọi trường hợp.

## 13. Transaction — Cách Làm Ổn Định Nhất

Dùng khi 1 nghiệp vụ cần **nhiều câu SQL cùng thành công hoặc cùng thất bại** (VD: trừ điểm + ghi log, tạo đơn hàng + trừ tồn kho).

**Cách ổn định nhất — đặt transaction ở Service, DAO nhận `Connection` được truyền vào (không tự mở connection riêng):**

```java
// DAO — thêm tham số Connection, dùng chung connection với các DAO khác trong cùng transaction
public void deductCredit(Connection conn, int studentId, int credit) throws SQLException {
    String sql = "UPDATE Students SET Credit = Credit - ? WHERE StudentId = ?";
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setInt(1, credit);
        stmt.setInt(2, studentId);
        stmt.executeUpdate();
    }
}
```

```java
// Service — nơi mở transaction, gọi nhiều DAO dùng chung 1 Connection
public ServiceResult<Void> transferCredit(int studentId, int credit) {
    try (Connection conn = dbContext.getConnection()) {
        conn.setAutoCommit(false); // B1: tắt auto-commit, bắt đầu transaction

        try {
            // B2: gọi các DAO liên quan, dùng chung 1 connection
            studentDAO.deductCredit(conn, studentId, credit);
            logDAO.insertLog(conn, studentId, "Trừ điểm: " + credit);

            conn.commit(); // B3: tất cả thành công -> lưu thật vào database
            return ServiceResult.ok(null);

        } catch (SQLException e) {
            conn.rollback(); // B4: có bước lỗi -> hủy hết, database về trạng thái trước đó
            e.printStackTrace();
            return ServiceResult.fail("Giao dịch thất bại, đã hoàn tác toàn bộ thay đổi");
        }

    } catch (SQLException e) {
        e.printStackTrace();
        return ServiceResult.fail("Lỗi kết nối database");
    }
}
```

**Vì sao đây là cách ổn định nhất — giải thích từng điểm:**
- **`try-with-resources` cho `Connection`** (`try (Connection conn = ...)`) — connection tự động đóng khi ra khỏi block, không sợ quên đóng gây rò rỉ connection.
- **`conn.setAutoCommit(false)`** — mặc định mỗi câu SQL tự commit ngay lập tức (auto-commit = true); tắt đi để nhiều câu SQL gộp chung thành **1 đơn vị giao dịch**, chỉ thật sự lưu khi gọi `commit()`.
- **DAO nhận `Connection` từ ngoài truyền vào** (thay vì tự `dbContext.getConnection()` riêng) — đảm bảo `deductCredit()` và `insertLog()` chạy trên **cùng 1 connection**, cùng 1 transaction. Nếu để mỗi DAO tự mở connection riêng, mỗi lệnh sẽ tự commit độc lập — mất hoàn toàn ý nghĩa transaction (không rollback đồng bộ được).
- **`rollback()` trong `catch` bên trong** — chỉ rollback đúng phần liên quan transaction này, không ảnh hưởng gì khác.
- **2 lớp `try-catch` lồng nhau** — lớp ngoài bắt lỗi *kết nối* (không mở được connection), lớp trong bắt lỗi *trong lúc chạy transaction* (rollback được vì đã có connection).

**Lưu ý:** vì DAO giờ có thêm tham số `Connection`, nếu method đó cũng được gọi ở nơi KHÔNG cần transaction (chỉ 1 câu SQL đơn lẻ), vẫn gọi bình thường với connection tự lấy từ `dbContext.getConnection()` — không bắt buộc mọi method DAO đều phải theo kiểu transaction, chỉ áp dụng khi nghiệp vụ thật sự cần gộp nhiều câu SQL.

## 14. Pagination

Áp dụng khi danh sách có khả năng dài (ước chừng trên 50-100 dòng) — tùy theo mục đích/số lượng/bối cảnh cụ thể của từng màn hình, không bắt buộc cứng cho mọi danh sách.

- **Luôn phân trang ở tầng SQL Server** bằng `OFFSET ... FETCH NEXT ... ROWS ONLY`, không lấy hết dữ liệu về Java rồi mới cắt — tốn bộ nhớ và chậm khi dữ liệu lớn.
  ```sql
  SELECT StudentId, Name, Average
  FROM Students
  ORDER BY CreatedDate DESC
  OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY
  ```

### Lưu ý quan trọng: STT (số thứ tự) khi sort "mới nhất lên đầu"

**Vấn đề thường gặp (case tệ):** đánh STT cố định theo 1 nguồn nào đó (VD: theo `StudentId` tăng dần) nhưng lại **sort hiển thị theo thứ tự khác** (mới nhất lên đầu) — khiến STT hiển thị bị đảo lộn, không còn ý nghĩa đếm số dòng nữa (VD: dòng đầu tiên hiển thị STT là 45, dòng tiếp theo là 12 — nhìn rất khó hiểu, người dùng không biết đang xem dòng thứ mấy trong tổng danh sách).

**Cách làm đúng:** STT hiển thị phải luôn được tính **dựa trên đúng vị trí hiển thị sau khi đã sort và phân trang** — không lấy từ 1 cột cố định nào trong database.
```java
// Service hoặc Servlet — tính STT dựa trên trang hiện tại + vị trí trong trang, KHÔNG lấy từ DB
int pageNumber = 2;      // trang hiện tại (bắt đầu từ 1)
int pageSize = 10;       // số dòng mỗi trang
int startIndex = (pageNumber - 1) * pageSize; // = 10

for (int i = 0; i < students.size(); i++) {
    int stt = startIndex + i + 1; // STT luôn liên tục đúng theo trang: trang 2 sẽ là 11, 12, 13...
    ...
}
```
- STT tính theo **vị trí sau khi sort**, nên khi đổi tiêu chí sort (mới nhất lên đầu / theo tên A-Z...), STT vẫn luôn đúng thứ tự 1, 2, 3... liên tục qua các trang — không bị nhảy số lộn xộn.
- **Không lưu STT vào database** — STT chỉ là thứ tự hiển thị tạm thời tại thời điểm xem trang đó, luôn tính lại mỗi lần load.

## 15. Lưu Ý Cuối

- **Comment:** Javadoc (mục đích + `@param`/`@return`) cho method quan trọng; **2 loại khung chương trình** — (1) khung chương trình chính ở cấp file/class (comment tổng quan các nhóm method), (2) khung chương trình từng bước bên trong 1 method (`// B1`, `// B2`...) — không giải thích logic phức tạp bên trong.
- **Format:** K&R, 4 spaces, không tab.
- **Style:** theo Mục 3, luôn PreparedStatement cho SQL Server; màn hình AJAX/REST API vẫn bám sát toàn bộ quy tắc, chỉ khác cách Controller trả dữ liệu.
- **Coding cơ bản:** 1 dòng = 1 logic, không gộp điều kiện/thao tác.
- **Tối ưu:** chỉ ở code đơn giản/cần thiết, không đụng database, bỏ qua nếu làm code khó hiểu hơn.
- **Utility:** validate/password dùng chung trong package `util`, hàm validate phải thuần (không I/O), chỉ gọi từ Service.
- **Entity/Setter:** setter chỉ gán, không validate/revert/print — validate ở Service qua `ValidationUtil`, trả `ServiceResult.fail(message)` rõ ràng; field bất biến (ID...) dùng `final`, không viết setter.
- **Thứ tự code (quan trọng nhất):** viết từ trên xuống theo đúng luồng chạy thực tế, chỉ constant ở đầu file, không gom nhóm logic cùng loại rải rác lại một chỗ nếu phá vỡ thứ tự đọc tự nhiên.
- **Servlet mapping:** bắt buộc `@WebServlet("/duong-dan")` ngay trên class — **cấm tạo `web.xml`** để mapping servlet/filter dưới mọi hình thức. **Ngoại lệ duy nhất:** `web.xml` được phép tồn tại chỉ để khai báo `<error-page>` (404/500) — không chứa `<servlet>`/`<filter>` nào.
- **pom.xml:** chỉ thêm dependency thật sự cần, luôn ghi version cụ thể (không dùng `LATEST`), `packaging` là `war`, `scope: provided` cho Servlet API.
- **Exception:** DAO throw lên, không tự catch; Service bắt cụ thể (`SQLException`), log lỗi thật bằng `e.printStackTrace()`, trả message an toàn cho user qua `ServiceResult.fail()`.
- **Session/Cookie (bắt buộc):** chỉ lưu `userId`/`role` vào session, timeout **7200 giây (2 tiếng)** trừ SRS yêu cầu khác, logout dùng `session.invalidate()`; Remember Me (cookie token dài hạn) chỉ làm nếu SRS yêu cầu, không tự ý thêm.
- **Authorization:** `AuthFilter` (`@WebFilter("/*")`) check đăng nhập, filter riêng theo role (VD: `@WebFilter("/admin/*")`) check quyền — không dùng `web.xml`.
- **Transaction:** mở ở Service (`conn.setAutoCommit(false)`), DAO nhận `Connection` truyền vào để dùng chung 1 transaction, `commit()`/`rollback()` đúng chỗ.
- **Pagination:** phân trang ở SQL Server (`OFFSET/FETCH`), STT tính theo vị trí trang hiện tại — không lấy từ cột cố định trong DB, tránh STT bị đảo lộn khi sort.
