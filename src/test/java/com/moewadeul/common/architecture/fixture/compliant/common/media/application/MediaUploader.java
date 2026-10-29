package com.moewadeul.common.architecture.fixture.compliant.common.media.application;

import com.moewadeul.common.architecture.fixture.compliant.common.media.domain.MediaRepository;
import org.springframework.transaction.annotation.Transactional;

public abstract class MediaUploader {

    private final MediaRepository mediaRepository;

    protected MediaUploader(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    @Transactional
    public void upload(String key) {
        mediaRepository.save(key);
    }
}
