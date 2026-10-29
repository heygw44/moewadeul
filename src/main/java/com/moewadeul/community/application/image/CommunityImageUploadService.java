package com.moewadeul.community.application.image;

import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.common.storage.ImageStorage.PresignedUpload;
import com.moewadeul.community.domain.image.CommunityImage;
import com.moewadeul.community.domain.image.CommunityImageRepository;
import com.moewadeul.community.infra.image.CommunityImageMapper;
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
public class CommunityImageUploadService {

    private static final String THUMBNAIL_KEY_SUFFIX = "_thumb.jpg";

    private final CommunityImageRepository communityImageRepository;
    private final CommunityImageMapper communityImageMapper;
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
        Instant now = clock.instant();
        requireUploadRoom(memberId, now);
        String baseKey = "community/" + memberId + "/" + UUID.randomUUID();
        String key = baseKey + "." + extension;
        CommunityImage.Thumbnail thumbnail = command.hasThumbnail()
                ? new CommunityImage.Thumbnail(baseKey + THUMBNAIL_KEY_SUFFIX, command.thumbnailSizeBytes())
                : null;
        CommunityImage image = communityImageRepository.saveAndFlush(
                CommunityImage.issue(memberId, key, command.contentType(), command.sizeBytes(), now, thumbnail));
        PresignedUpload upload = imageStorage.presignUpload(key, command.contentType(), command.sizeBytes());
        return new CommunityImageUpload(
                image.getId(), upload.url(), upload.headers(), upload.expiresAt(), presignThumbnail(thumbnail));
    }

    private void requireUploadRoom(long memberId, Instant now) {
        // 같은 회원의 발급을 한 줄로 세운다. 그래야 상한에 한 장 모자랄 때 동시에 온 요청이 같은 수를 읽고 함께 통과하지 못한다.
        communityImageMapper.lockMember(memberId);
        List<Instant> createdAts = communityImageMapper.selectUnattachedCreatedAtsAfter(
                memberId, now.minus(CommunityImageCleanupService.RETENTION), CommunityImage.MAX_UNATTACHED_PER_MEMBER);
        if (createdAts.size() >= CommunityImage.MAX_UNATTACHED_PER_MEMBER) {
            Instant oldestExpiresAt = createdAts.get(0).plus(CommunityImageCleanupService.RETENTION);
            throw new RateLimitedException(
                    CommonErrorCode.IMAGE_UPLOAD_LIMITED, Duration.between(now, oldestExpiresAt));
        }
    }

    private PresignedUpload presignThumbnail(CommunityImage.Thumbnail thumbnail) {
        if (thumbnail == null) {
            return null;
        }
        return imageStorage.presignUpload(
                thumbnail.objectKey(), CommunityImage.THUMBNAIL_CONTENT_TYPE, thumbnail.sizeBytes());
    }
}
