package com.ai.model.dto.library;

import lombok.Data;

import java.util.List;

@Data
public class LibraryFileTagsRequest {

    private List<Long> tagIds;
}
