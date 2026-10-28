package com.moewadeul.community.application.post;

import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.community.infra.image.CommunityImageMapper;
import com.moewadeul.community.infra.post.CommunityPostMapper;
import com.moewadeul.community.infra.post.CommunityPostRow;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CommunityPostItemReader {

    private final CommunityPostMapper communityPostMapper;
    private final CommunityImageMapper communityImageMapper;
    private final ImageStorage imageStorage;

    CommunityPostItem toListItem(CommunityPostRow row) {
        String thumbnailUrl = row.thumbnailKey() == null ? null : imageStorage.presignView(row.thumbnailKey());
        return CommunityPostItem.from(row, thumbnailUrl, List.of());
    }

    Optional<CommunityPostItem> findDetail(long postId, Long viewerId) {
        return communityPostMapper.selectActiveById(postId, viewerId).map(row -> {
            List<CommunityPostImage> images = communityImageMapper.selectByPostId(postId).stream()
                    .map(image -> new CommunityPostImage(image.imageId(), imageStorage.presignView(image.objectKey())))
                    .toList();
            return CommunityPostItem.from(row, null, images);
        });
    }
}
