package com.demandline.library.service.model.input;

import java.util.List;

public record LoanInput(
        Integer memberId,
        List<Integer> bookIds
) {}
