package com.demandline.library.service.model.input;

import com.demandline.library.service.model.Role;

public record UserUpdateInput(
        Integer id,
        String name,
        String email,
        String password,
        String roleId
) {}
