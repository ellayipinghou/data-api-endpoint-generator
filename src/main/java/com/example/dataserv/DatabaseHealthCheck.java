
package com.example.dataserv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// verify database connectivity on application startup
@Component
public class DatabaseHealthCheck {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseHealthCheck.class);

    public DatabaseHealthCheck(JdbcTemplate jdbcTemplate) {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        logger.info("Database connection verified: {}", result);
    }
}