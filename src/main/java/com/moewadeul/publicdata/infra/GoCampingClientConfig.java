package com.moewadeul.publicdata.infra;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoCampingProperties.class)
public class GoCampingClientConfig {}
