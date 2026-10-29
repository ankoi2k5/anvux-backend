# AnVux Backend

REST API cho **AnVux** — trợ lý cuộc sống dùng AI dành cho học sinh, sinh viên và người trẻ mới đi làm: quản lý dự án, công việc, nhắc việc và lập kế hoạch bằng AI.

Ứng dụng Android: [anvux-android](https://github.com/TEN_GITHUB/anvux-android)

## Công nghệ

- Java 21, Spring Boot 4.1.2 (Web, Validation, Data JPA, Actuator)
- PostgreSQL 17 chạy bằng Docker Compose
- Flyway (quản lý migration cơ sở dữ liệu)
- springdoc-openapi (Swagger UI)

## Yêu cầu

- JDK 21
- Docker Desktop

## Chạy ở máy dev

1. Bật cơ sở dữ liệu:

   ```bash
   docker compose up -d
   ```

2. Chạy ứng dụng, một trong hai cách:

   - Trong IntelliJ: mở `AnvuxBackendApplication` rồi bấm Run
   - Hoặc dùng dòng lệnh: `./mvnw spring-boot:run` (Windows PowerShell: `.\mvnw.cmd spring-boot:run`)

3. Mở các địa chỉ:

   | Địa chỉ | Mô tả |
   | --- | --- |
   | http://localhost:8080/api/v1/ping | Kiểm tra API sống |
   | http://localhost:8080/actuator/health | Trạng thái ứng dụng |
   | http://localhost:8080/swagger-ui.html | Tài liệu API (Swagger UI) |

## Cấu hình

Các biến môi trường (đều có giá trị mặc định cho máy dev, xem `.env.example`):

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `DB_HOST` | `localhost` | Máy chủ PostgreSQL |
| `DB_PORT` | `5433` | Cổng PostgreSQL trên máy host |
| `DB_NAME` | `anvux` | Tên cơ sở dữ liệu |
| `DB_USER` | `anvux` | Tài khoản |
| `DB_PASSWORD` | `anvux_dev_password` | Mật khẩu (chỉ dùng cho dev) |

Cổng mặc định là 5433 để không đụng PostgreSQL cài sẵn trên máy (thường dùng 5432).
Khi triển khai thật, truyền mật khẩu mạnh bằng biến môi trường, không dùng mật khẩu mặc định.

## Cấu trúc mã nguồn

```
src/main/java/com/anvux/
├── common/    # API dùng chung (ping)
├── config/    # Cấu hình
├── auth/      # Đăng ký, đăng nhập, JWT
├── user/      # Người dùng
├── project/   # Dự án
├── task/      # Công việc
├── ai/        # Lập kế hoạch bằng AI
└── sync/      # Đồng bộ offline
```

## Tiến độ

| Hạng mục | Trạng thái |
| --- | --- |
| Khởi tạo project, Docker Compose, Swagger | Xong |
| Đăng ký, đăng nhập, JWT | Tuần 2 |
| Dự án và công việc | Tuần 3 |
| Nhắc việc | Tuần 4 |
| AI Action Planner | Tuần 5 |
| Đồng bộ offline | Tuần 6 |
| Thống kê, bảo mật, kiểm thử | Tuần 7 |
| Đóng gói và phát hành | Tuần 8 |