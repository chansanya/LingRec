package com.lingrec.model.dto;

import com.lingrec.model.entity.Resource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendPageResult {
    private String sessionId;
    private List<Resource> items;
    private int nextCursor;
    private boolean hasMore;
    private int total;
}
