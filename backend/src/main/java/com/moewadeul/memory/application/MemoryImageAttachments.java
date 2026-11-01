package com.moewadeul.memory.application;

import com.moewadeul.common.image.application.ImageAttachments;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.memory.domain.image.MemoryImage;
import com.moewadeul.memory.domain.image.MemoryImageRepository;
import org.springframework.stereotype.Component;

@Component
class MemoryImageAttachments extends ImageAttachments<MemoryImage> {

    MemoryImageAttachments(MemoryImageRepository memoryImageRepository, ImageStorage imageStorage) {
        super(MemoryImage.KIND, memoryImageRepository, imageStorage);
    }
}
