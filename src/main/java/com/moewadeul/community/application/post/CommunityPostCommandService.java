package com.moewadeul.community.application.post;

import com.moewadeul.common.web.PatchField;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommunityPostCommandService {

    private final CommunityPostWriter communityPostWriter;
    private final CommunityImageAttachmentChecker communityImageAttachmentChecker;

    public long write(long memberId, CommunityPostWriteCommand command) {
        communityImageAttachmentChecker.verify(memberId, null, command.imageIds());
        return communityPostWriter.write(memberId, command);
    }

    public CommunityPostItem revise(long memberId, long postId, CommunityPostReviseCommand command) {
        PatchField<List<Long>> imageIds = command.imageIds();
        if (imageIds.present()) {
            communityPostWriter.requireOwnActivePost(memberId, postId);
            if (imageIds.value() == null) {
                throw CommunityImageAttachmentChecker.invalid("imageIds는 비울 수 없어요. 이미지를 모두 떼려면 빈 배열을 보내 주세요.");
            }
            communityImageAttachmentChecker.verify(memberId, postId, imageIds.value());
        }
        return communityPostWriter.revise(memberId, postId, command);
    }

    public void delete(long memberId, long postId) {
        communityPostWriter.delete(memberId, postId);
    }
}
