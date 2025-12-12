package com.demandline.library.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation for permission-based authorization
 * Usage: @RequiresPermission("BOOK:CREATE")
 * Can be used on controller methods to enforce permission checks
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String[] value();
}

