package sajo.AiLock_bonTest.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RouterRaw(
        @JsonProperty("intent") IntentCode intent,
        @JsonProperty("tone") ToneCode tone
) {
    public enum IntentCode { U, W, S }
    public enum ToneCode { A, N }
}