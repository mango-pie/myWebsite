package com.ai.model.dto.library;

import lombok.Data;

import java.util.List;

@Data
public class LibraryFilesCollectionsRequest {

    private List<Long> fileIds;

    private Long collectionId;
}
