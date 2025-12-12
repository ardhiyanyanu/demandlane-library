package com.demandline.library.controller;

import com.demandline.library.security.RequiresPermission;
import com.demandline.library.service.MemberService;
import com.demandline.library.service.model.Member;
import com.demandline.library.service.model.filter.MemberFilter;
import com.demandline.library.service.model.input.MemberUpdateInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Member Management Controller
 * Handles member registration and management endpoints
 * Requires MEMBER related permissions based on operation
 */
@RestController
@RequestMapping("/library/admin/members")
@Tag(name = "Member Management", description = "Member registration and management endpoints (Front Desk Staff access)")
@SecurityRequirement(name = "Bearer Authentication")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @RequiresPermission("MEMBER:READ")
    @Operation(
        summary = "List All Members",
        description = "Retrieve list of all registered members with their details and membership status.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Members list retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (MEMBER:READ required)")
    })
    public ResponseEntity<List<MemberResponse>> listMembers(
        @Parameter(description = "Search term for name") @RequestParam(required = false) String searchName,
        @Parameter(description = "Search term for email") @RequestParam(required = false) String searchEmail,
        @Parameter(description = "Page number for pagination") @RequestParam(defaultValue = "0") Integer page,
        @Parameter(description = "Page size for pagination") @RequestParam(defaultValue = "20") Integer size) {
        var members = memberService.getAllMembers(new MemberFilter(
                Optional.ofNullable(searchName),
                Optional.ofNullable(searchEmail)
        ), size, page * size);
        return ResponseEntity.ok(members.stream().map(MemberResponse::new).toList());
    }

    @PutMapping("/{id}")
    @RequiresPermission("MEMBER:UPDATE")
    @Operation(
        summary = "Update Member Information",
        description = "Update member information such as name, email, phone number, or address.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Member information updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (MEMBER:UPDATE required)"),
        @ApiResponse(responseCode = "404", description = "Member not found")
    })
    public ResponseEntity<MemberResponse> updateMember(
        @Parameter(description = "Member ID") @PathVariable Integer id,
        @RequestBody MemberUpdateRequest request) {
        var updatedMember = memberService.updateMember(new MemberUpdateInput(id,
                request.name(),
                request.email(),
                request.password(),
                request.phone(),
                request.address()));
        return ResponseEntity.ok(new MemberResponse(updatedMember));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("MEMBER:DELETE")
    @Operation(
        summary = "Deactivate Member Account",
        description = "Deactivate a member account. The member is marked as inactive instead of being deleted. " +
                      "Inactive members cannot borrow new books but can still return existing loans.",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Member account deactivated successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (MEMBER:DELETE required)"),
        @ApiResponse(responseCode = "404", description = "Member not found"),
        @ApiResponse(responseCode = "409", description = "Cannot deactivate member with active loans")
    })
    public ResponseEntity<Void> deactivateMember(@Parameter(description = "Member ID") @PathVariable String id) {
        memberService.deleteMember(id);
        return ResponseEntity.ok().build();
    }

    // Request/Response DTOs
    public record MemberUpdateRequest(
        String name,
        String email,
        String password,
        String phone,
        String address
    ) {}

    public record MemberResponse(
        Integer id,
        Integer userId,
        String name,
        String email,
        String phone,
        String address,
        Boolean isActive
    ) {
        public MemberResponse(Member member) {
            this(
                    member.id(),
                    member.user().id(),
                    member.user().name(),
                    member.user().email(),
                    member.phoneNumber(),
                    member.address(),
                    member.isActive()
            );
        }
    }
}

