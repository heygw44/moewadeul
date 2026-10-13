package com.moewadeul.aisearch.infra;

import com.moewadeul.aisearch.application.AiSearchProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OpenAiProperties.class, AiSearchProperties.class})
public class OpenAiClientConfig {}
