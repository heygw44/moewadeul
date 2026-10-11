package com.moewadeul.common.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImageStorageProperties.class)
public class ImageStorageConfig {}
