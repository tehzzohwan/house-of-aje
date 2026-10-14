package com.houseofaje.staff;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin staff management: list, create, toggle availability, and issue registration invites. */
@RestController
@RequestMapping("/api/admin/staff")
public class StaffAdminController {

    private final StaffAdminService staff;
    private final StaffInviteService invites;

    public StaffAdminController(StaffAdminService staff, StaffInviteService invites) {
        this.staff = staff;
        this.invites = invites;
    }

    @GetMapping
    public List<Staff> list() {
        return staff.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Staff create(@RequestBody CreateStaffRequest request) {
        return staff.create(request);
    }

    @PutMapping("/{id}")
    public Staff update(@PathVariable UUID id, @RequestBody CreateStaffRequest request) {
        return staff.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public Staff activate(@PathVariable UUID id) {
        return staff.setActive(id, true);
    }

    @PostMapping("/{id}/deactivate")
    public Staff deactivate(@PathVariable UUID id) {
        return staff.setActive(id, false);
    }

    @PostMapping("/{id}/invite")
    public StaffInviteResponse invite(@PathVariable UUID id) {
        return new StaffInviteResponse(invites.createInvite(id));
    }

    @GetMapping("/{id}/invite")
    public StaffInviteStatus inviteStatus(@PathVariable UUID id) {
        return invites.pendingInviteToken(id)
                .map(token -> new StaffInviteStatus(true, token))
                .orElseGet(() -> new StaffInviteStatus(false, null));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        staff.delete(id);
    }
}
