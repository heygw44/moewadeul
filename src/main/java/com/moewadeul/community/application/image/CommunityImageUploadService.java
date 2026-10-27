package com.moewadeul.community.application.image;

import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.common.storage.ImageStorage.PresignedUpload;
import com.moewadeul.community.domain.image.CommunityImage;
import com.moewadeul.community.domain.image.CommunityImageRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityImageUploadService {

    private static final String THUMBNAIL_KEY_SUFFIX = "_thumb.jpg";

    private final CommunityImageRepository communityImageRepository;
    private final ImageStorage imageStorage;
    private final Clock clock;

    @Transactional
    public CommunityImageUpload issue(long memberId, CommunityImageUploadCommand command) {
        String extension = CommunityImage.extensionOf(command.contentType())
                .orElseThrow(() -> new InvalidFieldException("contentType", "허용하지 않는 이미지 형식이에요."));
        if (!CommunityImage.isAllowedSize(command.sizeBytes())) {
            throw new InvalidFieldException("sizeBytes", "이미지 크기는 1바이트 이상 5MB 이하여야 해요.");
        }
        if (command.hasThumbnail() && !CommunityImage.isAllowedThumbnailSize(command.thumbnailSizeBytes())) {
            throw new InvalidFieldException("thumbnailSizeBytes", "썸네일 크기는 1바이트 이상 256KB 이하여야 해요.");
        }
        String baseKey = "community/" + memberId + "/" + UUID.randomUUID();
        String key = baseKey + "." + extension;
        CommunityImage.Thumbnail thumbnail = command.hasThumbnail()
                ? new CommunityImage.Thumbnail(baseKey + THUMBNAIL_KEY_SUFFIX, command.thumbnailSizeBytes())
                : null;
        CommunityImage image = communityImageRepository.saveAndFlush(CommunityImage.issue(
                memberId, key, command.contentType(), command.sizeBytes(), clock.instant(), thumbnail));
        PresignedUpload upload = imageStorage.presignUpload(key, command.contentType(), command.sizeBytes());
        return new CommunityImageUpload(
                image.getId(), upload.url(), upload.headers(), upload.expiresAt(), presignThumbnail(thumbnail));
    }

    private PresignedUpload presignThumbnail(CommunityImage.Thumbnail thumbnail) {
        if (thumbnail == null) {
            return null;
        }
        return imageStorage.presignUpload(
                thumbnail.objectKey(), CommunityImage.THUMBNAIL_CONTENT_TYPE, thumbnail.sizeBytes());
    }
}
