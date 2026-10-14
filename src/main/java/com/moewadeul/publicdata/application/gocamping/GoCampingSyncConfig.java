package com.moewadeul.publicdata.application.gocamping;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoCampingSyncProperties.class)
public class GoCampingSyncConfig {}
