package com.moewadeul.common.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public class WireMockResetExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        SpringExtension.getApplicationContext(context)
                .getBean(WireMockServer.class)
                .resetAll();
    }
}
