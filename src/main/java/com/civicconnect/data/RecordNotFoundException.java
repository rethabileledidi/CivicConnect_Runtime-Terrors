package com.civicconnect.data;

public class RecordNotFoundException extends DataAccessException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public RecordNotFoundException(String message) { super(message); }
}
