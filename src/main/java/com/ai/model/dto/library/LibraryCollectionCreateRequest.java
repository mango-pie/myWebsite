package com.ai.model.dto.library;

import lombok.Data;

@Data
public class LibraryCollectionCreateRequest {

    private String name;

    /** 与标签共用同一调色板，缺省 blue */
    private String color;
}
