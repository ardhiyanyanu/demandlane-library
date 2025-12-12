package com.demandline.library.service;

import com.demandline.library.repository.UserRepository;
import com.demandline.library.service.model.Role;
import com.demandline.library.service.model.User;
import com.demandline.library.service.model.input.UserInput;
import com.demandline.library.service.model.input.UserUpdateInput;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isEmailRegistered(String email) {
        var user = userRepository.findByEmail(email);
        return user.isPresent();
    }

    public User registerUser(UserInput userInput) {
        // TODO: Implement the logic to register a new user
        return null;
    }

    public User updateUser(UserUpdateInput updatedUser) {
        return null;
    }

    public void deleteUser(String userId) {

    }

    public List<User> getAllUsers(boolean includeMembers, int limit, int offset) {
        //userRepository.
        return List.of();
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email).map(this::mapToUser);
    }

    private User mapToUser(com.demandline.library.repository.model.User userEntity) {
        return new User(
                userEntity.getId(),
                userEntity.getName(),
                userEntity.getEmail(),
                userEntity.getPassword(),
                new Role(
                        userEntity.getRole().getId(),
                        userEntity.getRole().getName(),
                        userEntity.getRole().getPermissions(),
                        userEntity.getRole().getCreatedAt(),
                        userEntity.getRole().getUpdatedAt()
                ),
                userEntity.getCreatedAt(),
                userEntity.getUpdatedAt(),
                userEntity.getActive()
        );
    }
}
