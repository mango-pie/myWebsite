package com.ai.model.vo.knowledge;

import lombok.Data;

import java.util.List;

@Data
public class LearningTreeVO {

    private LearningDomainVO domain;
    private List<LearningBranchVO> branches;
    private boolean snapshotTruncated;
    private String snapshotMessage;
}
