package com.moewadeul.memory.application.image;

import com.moewadeul.common.image.application.ImageUploadIssuer;
import com.moewadeul.common.image.domain.AttachableImage.Thumbnail;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.memory.domain.image.MemoryImage;
import com.moewadeul.memory.domain.image.MemoryImageRepository;
import com.moewadeul.memory.infra.image.MemoryImageMapper;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class MemoryImageUploadService extends ImageUploadIssuer<MemoryImage> {

    MemoryImageUploadService(
            MemoryImageRepository memoryImageRepository,
            MemoryImageMapper memoryImageMapper,
            ImageStorage imageStorage,
            Clock clock) {
        super(MemoryImage.KIND, memoryImageRepository, memoryImageMapper, imageStorage, clock);
    }

    @Override
    protected MemoryImage newImage(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now, Thumbnail thumbnail) {
        return MemoryImage.issue(memberId, objectKey, contentType, sizeBytes, now, thumbnail);
    }
}
