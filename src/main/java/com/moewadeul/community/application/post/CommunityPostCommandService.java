package com.moewadeul.community.application.post;

import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.common.web.PatchField;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommunityPostCommandService {

    private static final String STORAGE_CHECK = "이미지가 저장소에 있는지 묻는 동안 DB 커넥션을 쥐면 안 된다.";

    private final CommunityPostWriter communityPostWriter;
    private final CommunityImageAttachmentPrecheck communityImageAttachmentPrecheck;

    public long write(long memberId, CommunityPostWriteCommand command) {
        OutsideTransaction.require(STORAGE_CHECK);
        communityImageAttachmentPrecheck.verify(memberId, null, command.imageIds());
        return communityPostWriter.write(memberId, command);
    }

    public CommunityPostItem revise(long memberId, long postId, CommunityPostReviseCommand command) {
        OutsideTransaction.require(STORAGE_CHECK);
        PatchField<List<Long>> imageIds = command.imageIds();
        if (imageIds.present()) {
            communityPostWriter.requireOwnActivePost(memberId, postId);
            if (imageIds.value() == null) {
                throw CommunityImageAttachmentPrecheck.invalid("imageIds는 비울 수 없어요. 이미지를 모두 떼려면 빈 배열을 보내 주세요.");
            }
            communityImageAttachmentPrecheck.verify(memberId, postId, imageIds.value());
        }
        return communityPostWriter.revise(memberId, postId, command);
    }

    public void delete(long memberId, long postId) {
        communityPostWriter.delete(memberId, postId);
    }
}
