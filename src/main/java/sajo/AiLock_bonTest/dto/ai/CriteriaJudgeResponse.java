package sajo.AiLock_bonTest.dto.ai;

public record CriteriaJudgeResponse(
        boolean necessity,
        boolean specificity,
        boolean feasibility,
        boolean timeSuitability,
        String reason
) {
}
