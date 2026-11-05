package com.moewadeul.community.application.image;

import com.moewadeul.common.image.application.ImageView;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.community.infra.image.CommunityImageMapper;
import com.moewadeul.community.infra.image.CommunityImageRow;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommunityImageReader {

    private final CommunityImageMapper communityImageMapper;
    private final ImageStorage imageStorage;

    public Map<Long, List<ImageView>> readByPostIds(Collection<Long> postIds) {
        return communityImageMapper.selectByPostIds(postIds).stream()
                .collect(Collectors.groupingBy(
                        CommunityImageRow::postId,
                        Collectors.mapping(
                                row -> new ImageView(row.imageId(), imageStorage.presignView(row.objectKey())),
                                Collectors.toList())));
    }
}
