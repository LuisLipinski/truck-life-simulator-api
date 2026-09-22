package com.luislipinski.trucklife.subscription.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionOwnerLock {
    private static final String LOCK_SQL = "SELECT id FROM users WHERE id = ? FOR UPDATE";

    private final JdbcTemplate jdbcTemplate;

    public SubscriptionOwnerLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lock(UUID userId) {
        jdbcTemplate.queryForObject(
                LOCK_SQL,
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                userId
        );
    }
}
