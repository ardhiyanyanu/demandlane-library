package com.demandline.library.service;

import com.demandline.library.repository.MemberRepository;
import com.demandline.library.repository.RoleRepository;
import com.demandline.library.repository.UserRepository;
import com.demandline.library.repository.model.MemberEntity;
import com.demandline.library.repository.model.UserEntity;
import com.demandline.library.service.model.Member;
import com.demandline.library.service.model.Role;
import com.demandline.library.service.model.User;
import com.demandline.library.service.model.filter.MemberFilter;
import com.demandline.library.service.model.input.MemberInput;
import com.demandline.library.service.model.input.MemberUpdateInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MemberService {
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository,
                        UserRepository userRepository,
                        RoleRepository roleRepository,
                        PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Member getMemberById(String memberId) {
        Integer id = Integer.valueOf(memberId);
        var memberEntity = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Member not found with id: " + memberId));

        return mapToMember(memberEntity);
    }

    public List<Member> getAllMembers(MemberFilter memberFilter, int limit, int offset) {
        List<MemberEntity> members;

        // Apply filters
        if (memberFilter.nameContains().isPresent() || memberFilter.emailContains().isPresent()) {
            String searchTerm = memberFilter.nameContains()
                    .or(() -> memberFilter.emailContains())
                    .orElse("");
            members = memberRepository.searchMembers(searchTerm);
        } else {
            members = memberRepository.findAllByIsActiveTrue();
        }

        // Apply pagination
        return members.stream()
                .skip(offset)
                .limit(limit)
                .map(this::mapToMember)
                .collect(Collectors.toList());
    }

    @Transactional
    public Member createMember(MemberInput memberInput) {
        // Check if email already exists
        if (userRepository.findByEmail(memberInput.email()).isPresent()) {
            throw new IllegalArgumentException("Email " + memberInput.email() + " is already registered");
        }

        // Get MEMBER role
        var memberRole = roleRepository.findByName("MEMBER")
                .orElseThrow(() -> new IllegalStateException("MEMBER role not found in database"));

        // Create user entity
        var userEntity = UserEntity.builder()
                .name(memberInput.name())
                .email(memberInput.email())
                .password(passwordEncoder.encode(memberInput.password()))
                .roleEntity(memberRole)
                .active(true)
                .build();

        var savedUser = userRepository.save(userEntity);

        // Create member entity
        var memberEntity = MemberEntity.builder()
                .userEntity(savedUser)
                .address(memberInput.address())
                .phoneNumber(memberInput.phoneNumber())
                .isActive(true)
                .build();

        var savedMember = memberRepository.save(memberEntity);
        log.info("Created member: {} (Email: {})", savedMember.getUserEntity().getName(),
                savedMember.getUserEntity().getEmail());

        return mapToMember(savedMember);
    }

    @Transactional
    public Member updateMember(MemberUpdateInput memberUpdateInput) {
        var memberEntity = memberRepository.findById(memberUpdateInput.id())
                .orElseThrow(() -> new IllegalArgumentException("Member not found with id: " + memberUpdateInput.id()));

        var userEntity = memberEntity.getUserEntity();

        // Update user fields
        if (memberUpdateInput.name() != null) {
            userEntity.setName(memberUpdateInput.name());
        }
        if (memberUpdateInput.email() != null && !memberUpdateInput.email().equals(userEntity.getEmail())) {
            // Check if new email already exists
            if (userRepository.findByEmail(memberUpdateInput.email()).isPresent()) {
                throw new IllegalArgumentException("Email " + memberUpdateInput.email() + " is already in use");
            }
            userEntity.setEmail(memberUpdateInput.email());
        }
        if (memberUpdateInput.password() != null) {
            userEntity.setPassword(passwordEncoder.encode(memberUpdateInput.password()));
        }

        // Update member fields
        if (memberUpdateInput.address() != null) {
            memberEntity.setAddress(memberUpdateInput.address());
        }
        if (memberUpdateInput.phoneNumber() != null) {
            memberEntity.setPhoneNumber(memberUpdateInput.phoneNumber());
        }

        userRepository.save(userEntity);
        var savedMember = memberRepository.save(memberEntity);

        log.info("Updated member: {} (ID: {})", savedMember.getUserEntity().getName(), savedMember.getId());
        return mapToMember(savedMember);
    }

    @Transactional
    public void deleteMember(String memberId) {
        Integer id = Integer.valueOf(memberId);
        var memberEntity = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Member not found with id: " + memberId));

        // Soft delete: mark as inactive
        memberEntity.setIsActive(false);
        memberEntity.getUserEntity().setActive(false);

        memberRepository.save(memberEntity);
        userRepository.save(memberEntity.getUserEntity());

        log.info("Deleted (deactivated) member: {} (ID: {})",
                memberEntity.getUserEntity().getName(), memberEntity.getId());
    }

    private Member mapToMember(MemberEntity memberEntity) {
        var userEntity = memberEntity.getUserEntity();
        var roleEntity = userEntity.getRoleEntity();

        var role = new Role(
                roleEntity.getId(),
                roleEntity.getName(),
                roleEntity.getPermissions(),
                roleEntity.getCreatedAt(),
                roleEntity.getUpdatedAt()
        );

        var user = new User(
                userEntity.getId(),
                userEntity.getName(),
                userEntity.getEmail(),
                userEntity.getPassword(),
                role,
                userEntity.getCreatedAt(),
                userEntity.getUpdatedAt(),
                userEntity.getActive()
        );

        return new Member(
                memberEntity.getId(),
                user,
                memberEntity.getAddress(),
                memberEntity.getPhoneNumber(),
                memberEntity.getCreatedAt(),
                memberEntity.getUpdatedAt(),
                memberEntity.getIsActive()
        );
    }
}
