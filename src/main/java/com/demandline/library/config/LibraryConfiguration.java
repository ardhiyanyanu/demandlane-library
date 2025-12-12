package com.demandline.library.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LibraryConfiguration {
    @Value("${library.maxBooksPerMember:5}")
    private int maxBooksPerMember;

    @Value("${library.loanPeriodDays:14}")
    private int loanPeriodDays;

    public int getMaxBooksPerMember() {
        return maxBooksPerMember;
    }

    public int getLoanPeriodDays() {
        return loanPeriodDays;
    }
}
