package com.anvux;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
public abstract class AbstractIntegrationTest {

    // Một container PostgreSQL duy nhất cho toàn bộ test, tự dọn khi kết thúc.
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static {
        postgres.start();
    }
}