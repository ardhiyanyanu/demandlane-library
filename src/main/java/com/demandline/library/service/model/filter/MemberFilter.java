package com.demandline.library.service.model.filter;

import java.util.Optional;

public record MemberFilter(
        Optional<String> nameContains,
        Optional<String> emailContains
) {}
