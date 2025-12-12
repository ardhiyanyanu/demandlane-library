package com.demandline.library.security;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;

/**
 * AOP Aspect for Permission-based Authorization
 * Checks if user has required permissions before method execution
 */
@Aspect
@Component
@Slf4j
public class PermissionCheckAspect {
    Logger logger = LoggerFactory.getLogger(PermissionCheckAspect.class);
    
    @Before("@annotation(requiresPermission)")
    public void checkPermission(JoinPoint joinPoint, RequiresPermission requiresPermission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }
        
        // Get required permissions
        String[] requiredPermissions = requiresPermission.value();
        
        // Get user authorities (permissions)
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        
        // Check if user has any of the required permissions
        boolean hasPermission = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> Arrays.asList(requiredPermissions).contains(auth));
        
        if (!hasPermission) {
            String userEmail = authentication.getName();
            String requiredPerms = String.join(",", requiredPermissions);
            logger.warn("Access denied for user: {} - Required permission(s): {}", userEmail, requiredPerms);
            throw new AccessDeniedException("Insufficient permissions. Required: " + requiredPerms);
        }
        
        logger.debug("Permission check passed for user: {} - Method: {}", authentication.getName(), joinPoint.getSignature().getName());
    }
}

