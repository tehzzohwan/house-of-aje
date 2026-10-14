package com.houseofaje.staff;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues and consumes single-use staff registration invites. */
@Service
public class StaffInviteService {

    private final StaffInviteRepository invites;
    private final StaffRepository staff;
    private final Duration inviteTtl;

    public StaffInviteService(StaffInviteRepository invites, StaffRepository staff,
            @Value("${hoa.staff-invite-ttl:P7D}") Duration inviteTtl) {
        this.invites = invites;
        this.staff = staff;
        this.inviteTtl = inviteTtl;
    }

    /** Creates a fresh invite for an existing staff member and returns its token. */
    @Transactional
    public String createInvite(UUID staffId) {
        if (staff.findById(staffId).isEmpty()) {
            throw new StaffNotFoundException(staffId);
        }
        // One invite at a time: return the still-valid token instead of generating
        // a new link for the same staff member.
        return invites.findValidByStaff(staffId)
                .map(StaffInvite::token)
                .orElseGet(() -> {
                    String token = UUID.randomUUID().toString().replace("-", "");
                    invites.insert(UUID.randomUUID(), staffId, token, Instant.now().plus(inviteTtl));
                    return token;
                });
    }

    /** The token of a pending invite for a staff member, or empty when none is outstanding. */
    public Optional<String> pendingInviteToken(UUID staffId) {
        return invites.findValidByStaff(staffId).map(StaffInvite::token);
    }

    /** Validates an invite token and marks it used; returns the target staff id. */
    @Transactional
    public UUID consume(String token) {
        StaffInvite invite = invites.findValidByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invite link"));
        if (!invites.markUsed(invite.id(), Instant.now())) {
            throw new IllegalArgumentException("Invite link has already been used");
        }
        return invite.staffId();
    }
}
