package com.moewadeul.common.image;

import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.image.AttachableImage.Thumbnail;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.common.storage.ImageStorage.PresignedUpload;
import com.moewadeul.common.transaction.InsideTransaction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public abstract class ImageUploadIssuer<T extends AttachableImage> {

    private static final String THUMBNAIL_KEY_SUFFIX = "_thumb.jpg";

    private final ImageKind kind;
    private final AttachableImageRepository<T> imageRepository;
    private final AttachableImageMapper imageMapper;
    private final ImageStorage imageStorage;
    private final Clock clock;

    protected ImageUploadIssuer(
            ImageKind kind,
            AttachableImageRepository<T> imageRepository,
            AttachableImageMapper imageMapper,
            ImageStorage imageStorage,
            Clock clock) {
        this.kind = kind;
        this.imageRepository = imageRepository;
        this.imageMapper = imageMapper;
        this.imageStorage = imageStorage;
        this.clock = clock;
    }

    protected abstract T newImage(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now, Thumbnail thumbnail);

    protected ImageUpload issueUpload(long memberId, ImageUploadCommand command) {
        InsideTransaction.require("회원 행을 잠근 채 세고 저장해야 발급 상한을 지킨다.");
        ImageMessages messages = kind.messages();
        String extension = AttachableImage.extensionOf(command.contentType())
                .orElseThrow(() -> new InvalidFieldException("contentType", messages.unsupportedType()));
        if (!AttachableImage.isAllowedSize(command.sizeBytes())) {
            throw new InvalidFieldException("sizeBytes", messages.sizeOutOfRange());
        }
        if (command.hasThumbnail() && !ThumbnailedImage.isAllowedThumbnailSize(command.thumbnailSizeBytes())) {
            throw new InvalidFieldException("thumbnailSizeBytes", "썸네일 크기는 1바이트 이상 256KB 이하여야 해요.");
        }
        Instant now = clock.instant();
        requireUploadRoom(memberId, now);
        String baseKey = kind.keyPrefix() + "/" + memberId + "/" + UUID.randomUUID();
        String key = baseKey + "." + extension;
        Thumbnail thumbnail = command.hasThumbnail()
                ? new Thumbnail(baseKey + THUMBNAIL_KEY_SUFFIX, command.thumbnailSizeBytes())
                : null;
        T image = imageRepository.saveAndFlush(
                newImage(memberId, key, command.contentType(), command.sizeBytes(), now, thumbnail));
        PresignedUpload upload = imageStorage.presignUpload(key, command.contentType(), command.sizeBytes());
        PresignedUpload thumbnailUpload =
                image.thumbnail().map(this::presignThumbnail).orElse(null);
        return new ImageUpload(image.getId(), upload.url(), upload.headers(), upload.expiresAt(), thumbnailUpload);
    }

    private void requireUploadRoom(long memberId, Instant now) {
        imageMapper.lockMember(memberId);
        int limit = kind.maxUnattachedPerMember();
        List<Instant> createdAts =
                imageMapper.selectUnattachedCreatedAtsAfter(memberId, now.minus(ImageCleanup.RETENTION), limit);
        if (createdAts.size() >= limit) {
            Instant oldestExpiresAt = createdAts.get(0).plus(ImageCleanup.RETENTION);
            throw new RateLimitedException(
                    CommonErrorCode.IMAGE_UPLOAD_LIMITED, Duration.between(now, oldestExpiresAt));
        }
    }

    private PresignedUpload presignThumbnail(Thumbnail thumbnail) {
        return imageStorage.presignUpload(
                thumbnail.objectKey(), ThumbnailedImage.THUMBNAIL_CONTENT_TYPE, thumbnail.sizeBytes());
    }
}
