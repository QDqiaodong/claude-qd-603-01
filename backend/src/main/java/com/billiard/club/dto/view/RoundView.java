package com.billiard.club.dto.view;

import java.util.List;

/**
 * 括号视图里的一轮（一个竖列）：胜者组/败者组各自分轮，总决赛与重置赛各自单列。
 * matchCodes 给出该列从上方到下方的场次顺序，前端按它排括号格子。
 */
public record RoundView(
        String roundKey,
        String bracketPart,
        String bracketPartName,
        Integer roundNo,
        String roundStage,
        String title,
        List<String> matchCodes) {
}
