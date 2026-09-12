package com.ai.model.vo.knowledge;

import lombok.Data;

@Data
public class ReadingSkillVO {

    private String name;
    private String description;
    /** Skill 正文（不含 YAML frontmatter），第二步按此格式出 Markdown */
    private String body;
}
