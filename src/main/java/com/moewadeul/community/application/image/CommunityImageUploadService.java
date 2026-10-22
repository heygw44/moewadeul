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

/**
 * 회원이 글에 붙일 이미지를 올릴 URL을 발급한다. 서버는 파일을 받지 않는다.
 *
 * <p>URL을 발급할 때 글에 붙지 않은 이미지 행을 먼저 만든다. 회원이 올리지 않거나 글에 붙이지 않은 이미지는 정리 작업이 24시간 뒤에 지운다.
 * 저장소 키는 서버가 {@code community/{회원 ID}/{UUID}.{확장자}}로 만든다. 사전 서명은 서버 안에서 계산하므로 저장소를 부르지 않고, 트랜잭션 안에서 해도 된다.
 *
 * <p>회원이 썸네일 크기를 함께 보내면 같은 UUID에 {@code _thumb.jpg}를 붙인 키로 썸네일 업로드 URL도 발급한다. 목록 화면은 원본 대신 이
 * 썸네일을 내려받는다.
 */
@Service
@RequiredArgsConstructor
public class CommunityImageUploadService {

    private static final String THUMBNAIL_KEY_SUFFIX = "_thumb.jpg";

    private final CommunityImageRepository communityImageRepository;
    private final ImageStorage imageStorage;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원이 command의 형식과 크기로 파일을 올릴 URL을 발급하고 이미지 행을 만든다. 썸네일 크기가 있으면 썸네일 업로드 URL도
     * 발급한다.
     *
     * <p>허용하지 않는 형식이거나 크기가 1바이트 미만이거나 5MB를 넘으면 INVALID_INPUT으로 거부한다. 썸네일 크기가 1바이트 미만이거나 256KB를
     * 넘어도 INVALID_INPUT으로 거부한다.
     */
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
