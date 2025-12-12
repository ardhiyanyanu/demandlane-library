package com.demandline.library.service.model.input;

import com.demandline.library.service.model.Role;

import java.util.List;

public record UserInput(
        String name,
        String email,
        String password,
        String roleId
) {}
