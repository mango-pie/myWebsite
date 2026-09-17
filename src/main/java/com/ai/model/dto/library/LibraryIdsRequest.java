package com.ai.model.dto.library;

import lombok.Data;

import java.util.List;

@Data
public class LibraryIdsRequest {

    private List<Long> ids;
}
