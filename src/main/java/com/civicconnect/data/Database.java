package com.civicconnect.data;

import org.postgresql.ds.PGSimpleDataSource;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Obtains the application's DataSource.
 *
 * <ol>
 *   <li>On Tomcat/GlassFish: the container-managed, pooled JNDI resource
 *       {@value #JNDI_NAME} (configured in META-INF/context.xml).</li>
 *   <li>Otherwise (tests, command line): environment variables CIVIC_DB_URL,
 *       CIVIC_DB_USER and CIVIC_DB_PASSWORD, or the same names as system properties
 *       in lower case with dots (civic.db.url ...).</li>
 * </ol>
 * No credentials are hard-coded or committed.
 */
public final class Database {

    public static final String JNDI_NAME = "java:comp/env/jdbc/CivicConnectDB";

    private Database() { }

    public static DataSource lookup() {
        DataSource jndi;
        try {
            jndi = (DataSource) new InitialContext().lookup(JNDI_NAME);
        } catch (NamingException | RuntimeException e) {
            return fromEnvironment("CIVIC_DB");
        }
        // If the container resource is mis-configured but environment variables exist, use them.
        try (Connection probe = jndi.getConnection()) {
            probe.isValid(2);
            return jndi;
        } catch (SQLException e) {
            String url = setting("CIVIC_DB_URL");
            return url == null || url.isBlank() ? jndi : fromEnvironment("CIVIC_DB");
        }
    }

    /** Builds an un-pooled DataSource from PREFIX_URL / PREFIX_USER / PREFIX_PASSWORD. */
    public static DataSource fromEnvironment(String prefix) {
        String url = setting(prefix + "_URL");
        if (url == null || url.isBlank()) {
            throw new DataAccessException("No database configured: define the JNDI resource " + JNDI_NAME
                    + " or set " + prefix + "_URL, " + prefix + "_USER and " + prefix + "_PASSWORD");
        }
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(url);
        String user = setting(prefix + "_USER");
        if (user != null) ds.setUser(user);
        String password = setting(prefix + "_PASSWORD");
        if (password != null) ds.setPassword(password);
        ds.setApplicationName("CivicConnect");
        return ds;
    }

    static String setting(String envName) {
        String value = System.getenv(envName);
        if (value == null) {
            value = System.getProperty(envName.toLowerCase().replace('_', '.'));
        }
        return value;
    }
}
