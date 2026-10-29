package com.moewadeul.memory.application.image;

import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.common.storage.ImageStorage.PresignedUpload;
import com.moewadeul.memory.domain.image.MemoryImage;
import com.moewadeul.memory.domain.image.MemoryImageRepository;
import com.moewadeul.memory.infra.image.MemoryImageMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemoryImageUploadService {

    private static final String THUMBNAIL_KEY_SUFFIX = "_thumb.jpg";

    private final MemoryImageRepository memoryImageRepository;
    private final MemoryImageMapper memoryImageMapper;
    private final ImageStorage imageStorage;
    private final Clock clock;

    @Transactional
    public MemoryImageUpload issue(long memberId, MemoryImageUploadCommand command) {
        String extension = MemoryImage.extensionOf(command.contentType())
                .orElseThrow(() -> new InvalidFieldException("contentType", "허용하지 않는 사진 형식이에요."));
        if (!MemoryImage.isAllowedSize(command.sizeBytes())) {
            throw new InvalidFieldException("sizeBytes", "사진 크기는 1바이트 이상 5MB 이하여야 해요.");
        }
        if (command.hasThumbnail() && !MemoryImage.isAllowedThumbnailSize(command.thumbnailSizeBytes())) {
            throw new InvalidFieldException("thumbnailSizeBytes", "썸네일 크기는 1바이트 이상 256KB 이하여야 해요.");
        }
        Instant now = clock.instant();
        requireUploadRoom(memberId, now);
        String baseKey = "memory/" + memberId + "/" + UUID.randomUUID();
        String key = baseKey + "." + extension;
        MemoryImage.Thumbnail thumbnail = command.hasThumbnail()
                ? new MemoryImage.Thumbnail(baseKey + THUMBNAIL_KEY_SUFFIX, command.thumbnailSizeBytes())
                : null;
        MemoryImage image = memoryImageRepository.saveAndFlush(
                MemoryImage.issue(memberId, key, command.contentType(), command.sizeBytes(), now, thumbnail));
        PresignedUpload upload = imageStorage.presignUpload(key, command.contentType(), command.sizeBytes());
        return new MemoryImageUpload(
                image.getId(), upload.url(), upload.headers(), upload.expiresAt(), presignThumbnail(thumbnail));
    }

    private void requireUploadRoom(long memberId, Instant now) {
        // 같은 회원의 발급을 한 줄로 세운다. 그래야 상한에 한 장 모자랄 때 동시에 온 요청이 같은 수를 읽고 함께 통과하지 못한다.
        memoryImageMapper.lockMember(memberId);
        List<Instant> createdAts = memoryImageMapper.selectUnattachedCreatedAtsAfter(
                memberId, now.minus(MemoryImageCleanupService.RETENTION), MemoryImage.MAX_UNATTACHED_PER_MEMBER);
        if (createdAts.size() >= MemoryImage.MAX_UNATTACHED_PER_MEMBER) {
            Instant oldestExpiresAt = createdAts.get(0).plus(MemoryImageCleanupService.RETENTION);
            throw new RateLimitedException(
                    CommonErrorCode.IMAGE_UPLOAD_LIMITED, Duration.between(now, oldestExpiresAt));
        }
    }

    private PresignedUpload presignThumbnail(MemoryImage.Thumbnail thumbnail) {
        if (thumbnail == null) {
            return null;
        }
        return imageStorage.presignUpload(
                thumbnail.objectKey(), MemoryImage.THUMBNAIL_CONTENT_TYPE, thumbnail.sizeBytes());
    }
}
