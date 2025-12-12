package com.demandline.library.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * AOP Configuration
 * Enables AspectJ auto-proxy for method-level security with @RequiresPermission annotation
 */
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
}

