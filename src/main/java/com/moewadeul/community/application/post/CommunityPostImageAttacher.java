package com.moewadeul.community.application.post;

import com.moewadeul.community.domain.image.CommunityImage;
import com.moewadeul.community.domain.image.CommunityImageRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CommunityPostImageAttacher {

    private final CommunityImageRepository communityImageRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean replace(long memberId, long postId, List<Long> imageIds) {
        List<Long> currentIds = communityImageRepository.findIdsByPostId(postId);
        if (currentIds.isEmpty() && imageIds.isEmpty()) {
            return false;
        }
        Set<Long> lockIds = new LinkedHashSet<>(imageIds);
        lockIds.addAll(currentIds);
        Map<Long, CommunityImage> locked = lock(lockIds);
        List<CommunityImage> next = new ArrayList<>();
        for (Long imageId : imageIds) {
            next.add(requireAttachable(locked.get(imageId), memberId, postId));
        }
        for (Long currentId : currentIds) {
            if (!imageIds.contains(currentId)) {
                locked.get(currentId).detach();
            }
        }
        for (int order = 0; order < next.size(); order++) {
            next.get(order).attachTo(postId, order);
        }
        return !currentIds.equals(imageIds);
    }

    private Map<Long, CommunityImage> lock(Collection<Long> ids) {
        Map<Long, CommunityImage> locked = new HashMap<>();
        communityImageRepository.findAllByIdForUpdate(ids).forEach(image -> locked.put(image.getId(), image));
        return locked;
    }

    private static CommunityImage requireAttachable(CommunityImage image, long memberId, long postId) {
        boolean attachable =
                image != null && image.isUploadedBy(memberId) && (!image.isAttached() || image.isAttachedTo(postId));
        if (!attachable) {
            throw CommunityImageAttachmentPrecheck.invalid(CommunityImageAttachmentPrecheck.UNUSABLE_MESSAGE_FOR_ANY_REASON);
        }
        return image;
    }
}
