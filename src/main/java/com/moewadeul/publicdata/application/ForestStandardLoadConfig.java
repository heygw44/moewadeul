package com.moewadeul.publicdata.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ForestStandardLoadProperties.class)
public class ForestStandardLoadConfig {}
