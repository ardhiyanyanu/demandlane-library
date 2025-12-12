package com.demandline.library.service;

import com.demandline.library.repository.UserRepository;
import com.demandline.library.service.model.User;
import com.demandline.library.service.model.input.UserInput;
import com.demandline.library.service.model.input.UserUpdateInput;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isEmailRegistered(String email) {
        // TODO: Implement the logic to check if the email is registered
        return true;
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
        return List.of();
    }

    public User getUserByEmail(String email) {
        return null;
    }
}
