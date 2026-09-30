package com.civicconnect.api;

import com.civicconnect.auth.AuthService;
import com.civicconnect.service.NotificationService;
import com.civicconnect.service.RequestService;

import javax.sql.DataSource;

/** Everything the API servlets need, built once by {@link BackendContextListener}. */
public record BackendServices(AuthService auth, RequestService requests, NotificationService notifications,
                              DataSource dataSource) {

    public static final String ATTRIBUTE = "civic.backendServices";
}
