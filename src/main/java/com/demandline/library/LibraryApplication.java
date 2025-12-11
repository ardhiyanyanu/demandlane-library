package com.demandline.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot Application for Singapore Car Parking Availability Service
 * 
 * This application provides real-time car parking availability information
 * in Singapore using data from the Singapore Open Data Portal.
 * 
 * Features:
 * - REST API for querying parking availability
 * - Redis-based geospatial data storage
 * - Periodic background updates of parking availability
 * - Health check endpoints
 * 
 * @author Demandline Team
 * @version 1.0.0
 */
@SpringBootApplication
public class LibraryApplication {

    /**
     * Main entry point for the Spring Boot application
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(LibraryApplication.class, args);
    }

}

