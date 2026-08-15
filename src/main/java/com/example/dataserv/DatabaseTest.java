
package com.example.dataserv;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseTest {

    public DatabaseTest(JdbcTemplate jdbcTemplate) {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT 1",
                Integer.class
        );

        System.out.println("Database returned: " + result);
    }
}