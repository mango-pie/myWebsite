package com.ai.model.dto.library;

import lombok.Data;

@Data
public class LibraryTagCreateRequest {

    private String name;

    /** sakura/violet/lilac/blue/mint/sun，缺省 sakura */
    private String color;
}
