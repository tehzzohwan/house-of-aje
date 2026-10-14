package com.houseofaje.staff;

import java.util.UUID;

public class StaffNotFoundException extends RuntimeException {

    public StaffNotFoundException(UUID id) {
        super("Staff member not found: " + id);
    }
}
