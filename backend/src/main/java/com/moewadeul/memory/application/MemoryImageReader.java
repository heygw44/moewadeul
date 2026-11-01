package com.moewadeul.memory.application;

import com.moewadeul.common.image.application.ImageView;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.memory.infra.image.MemoryImageMapper;
import com.moewadeul.memory.infra.image.MemoryImageRow;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryImageReader {

    private final MemoryImageMapper memoryImageMapper;
    private final ImageStorage imageStorage;

    public List<ImageView> readByMemoryId(long memoryId) {
        return memoryImageMapper.selectByMemoryIds(List.of(memoryId)).stream()
                .map(this::toPhoto)
                .toList();
    }

    public Map<Long, List<ImageView>> readByMemoryIds(Collection<Long> memoryIds) {
        return memoryImageMapper.selectByMemoryIds(memoryIds).stream()
                .collect(Collectors.groupingBy(
                        MemoryImageRow::memoryId, Collectors.mapping(this::toPhoto, Collectors.toList())));
    }

    public String viewUrlOf(String objectKey) {
        return imageStorage.presignView(objectKey);
    }

    private ImageView toPhoto(MemoryImageRow row) {
        return new ImageView(row.imageId(), imageStorage.presignView(row.objectKey()));
    }
}
