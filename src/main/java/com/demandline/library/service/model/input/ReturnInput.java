package com.demandline.library.service.model.input;

import java.util.List;

public record ReturnInput(
        Integer memberId,
        List<ReturnPairInput> returnPairInputs
) {}
