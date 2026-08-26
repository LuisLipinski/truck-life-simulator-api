package com.luislipinski.trucklife.career.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CareerOwnerLock {

    private static final String LOCK_OWNER_SQL = "SELECT id FROM users WHERE id = ? FOR UPDATE";

    private final JdbcTemplate jdbcTemplate;

    public CareerOwnerLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lock(UUID userId) {
        jdbcTemplate.queryForObject(
                LOCK_OWNER_SQL,
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                userId
        );
    }
}
