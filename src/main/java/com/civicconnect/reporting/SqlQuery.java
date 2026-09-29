package com.civicconnect.reporting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/** A SQL string with its positional parameters (always bound, never concatenated). */
public record SqlQuery(String sql, List<Object> params) {

    public PreparedStatement prepare(Connection c) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
        return ps;
    }
}
