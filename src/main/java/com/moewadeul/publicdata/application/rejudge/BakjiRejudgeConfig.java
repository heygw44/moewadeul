package com.moewadeul.publicdata.application.rejudge;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BakjiRejudgeProperties.class)
public class BakjiRejudgeConfig {}
