package com.moewadeul.publicdata.application.forest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ForestLoadProperties.class)
public class ForestLoadConfig {}
