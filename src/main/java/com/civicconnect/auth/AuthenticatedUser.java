package com.civicconnect.auth;

/**
 * The signed-in user, kept in the HTTP session. Holds no password material.
 * The API layer also copies {@code role} into the session attribute "userRole",
 * which Rethabile's ManagementAccessFilter reads for /dashboard and /reports.
 */
public record AuthenticatedUser(long userId, String email, String fullName, UserRole role,
                                String phone, String municipality) implements java.io.Serializable { }
