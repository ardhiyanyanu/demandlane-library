package com.demandline.library.service.model.input;

public record MemberUpdateInput(
        Integer id,
        String name,
        String email,
        String password,
        String address,
        String phoneNumber
) {}
