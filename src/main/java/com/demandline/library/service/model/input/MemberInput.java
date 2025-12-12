package com.demandline.library.service.model.input;

public record MemberInput(
        String name,
        String email,
        String password,
        String address,
        String phoneNumber
) {}
