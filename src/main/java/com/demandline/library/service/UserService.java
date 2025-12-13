package com.demandline.library.service;

import com.demandline.library.observability.MetricsService;
import com.demandline.library.repository.UserRepository;
import com.demandline.library.repository.RoleRepository;
import com.demandline.library.repository.model.UserEntity;
import com.demandline.library.service.model.User;
import com.demandline.library.service.model.Role;
import com.demandline.library.service.model.input.UserInput;
import com.demandline.library.service.model.input.UserUpdateInput;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MetricsService metricsService;

    public UserService(UserRepository userRepository,
                      RoleRepository roleRepository,
                      PasswordEncoder passwordEncoder,
                      MetricsService metricsService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.metricsService = metricsService;
    }

    /**
     * Track successful login
     * Should be called from AuthController after successful authentication
     */
    public void trackLoginSuccess() {
        metricsService.incrementLoginSuccess();
    }

    /**
     * Track failed login
     * Should be called from AuthController after failed authentication
     */
    public void trackLoginFailure() {
        metricsService.incrementLoginFailure();
    }

    public boolean isEmailRegistered(String email) {
        var user = userRepository.findByEmail(email);
        return user.isPresent();
    }

    @Transactional
    public User registerUser(UserInput userInput) {
        var roleOpt = roleRepository.findById(Integer.valueOf(userInput.roleId()));
        var roleEntity = roleOpt.orElseThrow(() -> new IllegalArgumentException("Role not found"));
        var entity = UserEntity.builder()
                .name(userInput.name())
                .email(userInput.email())
                .password(passwordEncoder.encode(userInput.password()))
                .roleEntity(roleEntity)
                .active(true)
                .build();
        var saved = userRepository.save(entity);
        metricsService.incrementUserRegistration();
        return mapToUser(saved);
    }

    @Transactional
    public User updateUser(UserUpdateInput updatedUser) {
        var userId = updatedUser.id();
        var entity = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (updatedUser.name() != null) entity.setName(updatedUser.name());
        if (updatedUser.email() != null) entity.setEmail(updatedUser.email());
        if (updatedUser.password() != null) entity.setPassword(passwordEncoder.encode(updatedUser.password()));
        if (updatedUser.roleId() != null) {
            var roleEntity = roleRepository.findById(Integer.valueOf(updatedUser.roleId()))
                    .orElseThrow(() -> new IllegalArgumentException("Role not found"));
            entity.setRoleEntity(roleEntity);
        }
        var saved = userRepository.save(entity);
        return mapToUser(saved);
    }

    @Transactional
    public void deleteUser(String userId) {
        var id = Integer.valueOf(userId);
        var entity = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        entity.setActive(false);
        userRepository.save(entity);
    }

    public List<User> getAllUsers(boolean includeMembers, int limit, int offset) {
        var users = userRepository.findAllByActiveTrue();
        var filtered = users.stream()
                .filter(u -> includeMembers || !"Member".equalsIgnoreCase(u.getRoleEntity().getName()))
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
        return filtered.stream().map(this::mapToUser).collect(Collectors.toList());
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email).map(this::mapToUser);
    }

    private User mapToUser(UserEntity userEntity) {
        return new User(
                userEntity.getId(),
                userEntity.getName(),
                userEntity.getEmail(),
                userEntity.getPassword(),
                new Role(
                        userEntity.getRoleEntity().getId(),
                        userEntity.getRoleEntity().getName(),
                        userEntity.getRoleEntity().getPermissions(),
                        userEntity.getRoleEntity().getCreatedAt(),
                        userEntity.getRoleEntity().getUpdatedAt()
                ),
                userEntity.getCreatedAt(),
                userEntity.getUpdatedAt(),
                userEntity.getActive()
        );
    }
}
