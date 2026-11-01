package com.moewadeul.common.web;

import com.moewadeul.common.patch.PatchField;
import org.springframework.stereotype.Component;
import tools.jackson.databind.module.SimpleModule;

@Component
class PatchFieldModule extends SimpleModule {

    PatchFieldModule() {
        super("PatchFieldModule");
        addDeserializer(PatchField.class, new PatchFieldDeserializer());
    }
}
