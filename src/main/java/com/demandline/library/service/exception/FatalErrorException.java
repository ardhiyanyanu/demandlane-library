package com.demandline.library.service.exception;

import java.io.IOException;

public class FatalErrorException extends Exception {
    public FatalErrorException() {
        super();
    }

    public FatalErrorException(String s, IOException e) {
        super(s, e);
    }
}
