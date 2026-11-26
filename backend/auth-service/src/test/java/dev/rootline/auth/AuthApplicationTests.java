package dev.rootline.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthApplicationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayCreatesOutboxAndInboxTables() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_name IN ('outbox_events', 'processed_events')
                """, Integer.class);
        assertThat(count).isEqualTo(2);
    }
}
