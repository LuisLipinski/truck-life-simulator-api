package com.luislipinski.trucklife.backup.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class InstantAwareJdbcTemplateConfiguration {

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new InstantAwareJdbcTemplate(dataSource);
    }

    static final class InstantAwareJdbcTemplate extends JdbcTemplate {

        InstantAwareJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public int update(String sql, Object... args) {
            Object[] normalizedArgs = Arrays.stream(args)
                    .map(InstantAwareJdbcTemplateConfiguration::normalize)
                    .toArray();
            return super.update(sql, normalizedArgs);
        }
    }

    private static Object normalize(Object value) {
        return value instanceof Instant instant ? Timestamp.from(instant) : value;
    }
}
