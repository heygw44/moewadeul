package com.moewadeul.publicdata.infra.gocamping;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoCampingProperties.class)
public class GoCampingClientConfig {}
