package sajo.AiLock_bonTest.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "feature")
public class FeatureProperties {

    private boolean abuseHardRejectEnabled = false;
}