package com.moewadeul.memory.application.image;

import com.moewadeul.common.image.AttachableImage.Thumbnail;
import com.moewadeul.common.image.ImageUpload;
import com.moewadeul.common.image.ImageUploadCommand;
import com.moewadeul.common.image.ImageUploadIssuer;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.memory.domain.image.MemoryImage;
import com.moewadeul.memory.domain.image.MemoryImageRepository;
import com.moewadeul.memory.infra.image.MemoryImageMapper;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoryImageUploadService extends ImageUploadIssuer<MemoryImage> {

    MemoryImageUploadService(
            MemoryImageRepository memoryImageRepository,
            MemoryImageMapper memoryImageMapper,
            ImageStorage imageStorage,
            Clock clock) {
        super(MemoryImage.KIND, memoryImageRepository, memoryImageMapper, imageStorage, clock);
    }

    @Transactional
    public ImageUpload issue(long memberId, ImageUploadCommand command) {
        return issueUpload(memberId, command);
    }

    @Override
    protected MemoryImage newImage(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now, Thumbnail thumbnail) {
        return MemoryImage.issue(memberId, objectKey, contentType, sizeBytes, now, thumbnail);
    }
}
