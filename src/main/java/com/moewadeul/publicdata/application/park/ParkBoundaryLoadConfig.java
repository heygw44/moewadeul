package com.moewadeul.publicdata.application.park;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ParkBoundaryLoadProperties.class)
public class ParkBoundaryLoadConfig {}
