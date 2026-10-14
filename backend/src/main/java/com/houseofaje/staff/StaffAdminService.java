package com.houseofaje.staff;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin staff management: list, create and toggle a staff member's availability. */
@Service
public class StaffAdminService {

    private final StaffRepository staff;

    public StaffAdminService(StaffRepository staff) {
        this.staff = staff;
    }

    public List<Staff> list() {
        return staff.findAll();
    }

    @Transactional
    public Staff create(CreateStaffRequest request) {
        String name = request.name() == null ? null : request.name().trim();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        String email = blankToNull(request.email());
        String phone = blankToNull(request.phone());
        String bio = blankToNull(request.bio());
        String photoUrl = blankToNull(request.photoUrl());
        if (email == null || phone == null) {
            throw new IllegalArgumentException("email and phone are required");
        }
        if (staff.emailExists(email)) {
            throw new IllegalArgumentException("A staff member with this email already exists");
        }
        UUID id = UUID.randomUUID();
        staff.insert(id, name, email, phone, bio, photoUrl, true, null);
        return staff.findById(id).orElseThrow(() -> new StaffNotFoundException(id));
    }

    @Transactional
    public Staff setActive(UUID id, boolean active) {
        if (!staff.setActive(id, active)) {
            throw new StaffNotFoundException(id);
        }
        return staff.findById(id).orElseThrow(() -> new StaffNotFoundException(id));
    }

    /** Soft-deletes a staff member (keeps booking history, blocks login). */
    @Transactional
    public void delete(UUID id) {
        if (!staff.delete(id)) {
            throw new StaffNotFoundException(id);
        }
    }

    @Transactional
    public Staff update(UUID id, CreateStaffRequest request) {
        if (staff.findById(id).isEmpty()) {
            throw new StaffNotFoundException(id);
        }
        String name = request.name() == null ? null : request.name().trim();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        String email = blankToNull(request.email());
        String phone = blankToNull(request.phone());
        String bio = blankToNull(request.bio());
        String photoUrl = blankToNull(request.photoUrl());
        if (email != null && staff.emailExists(email, id)) {
            throw new IllegalArgumentException("A staff member with this email already exists");
        }
        staff.update(id, name, email, phone, bio, photoUrl);
        return staff.findById(id).orElseThrow(() -> new StaffNotFoundException(id));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
