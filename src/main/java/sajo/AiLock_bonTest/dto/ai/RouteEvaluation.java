package sajo.AiLock_bonTest.dto.ai;

import sajo.AiLock_bonTest.decider.llm.router.RouterLogprobExtractor;

public record RouteEvaluation(
        RouterRaw raw,
        RouterLogprobExtractor.IntentConfidence intentConfidence
) {
}