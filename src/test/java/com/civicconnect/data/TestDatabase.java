package com.civicconnect.data;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration-test support. Tests using this are SKIPPED unless CIVIC_TEST_DB_URL is set, e.g.
 * <pre>
 *   CIVIC_TEST_DB_URL=jdbc:postgresql://localhost:5432/civicconnect_test
 *   CIVIC_TEST_DB_USER=civic_owner   CIVIC_TEST_DB_PASSWORD=...
 * </pre>
 * WARNING: the "civic" schema in that database is dropped and rebuilt from database/V1..V4.
 * Never point it at a real database.
 */
public final class TestDatabase {

    private static DataSource dataSource;

    private TestDatabase() { }

    public static synchronized DataSource freshDatabase() throws Exception {
        String url = Database.setting("CIVIC_TEST_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "CIVIC_TEST_DB_URL not set - database tests skipped");
        if (dataSource == null) {
            dataSource = Database.fromEnvironment("CIVIC_TEST_DB");
        }
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS civic CASCADE; CREATE SCHEMA civic;");
            for (String file : new String[]{"V1__schema.sql", "V2__reporting_views.sql",
                    "V3__reference_data.sql", "V4__sample_data_dev_only.sql"}) {
                st.execute(read(file));
            }
        }
        return dataSource;
    }

    private static String read(String file) throws IOException {
        Path p = Path.of("database", file);
        if (!Files.exists(p)) p = Path.of("..", "database", file);
        return Files.readString(p);
    }
}
