package io.litoria.config;

import java.util.Map;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "litoria")
public interface LitoriaConfig {

    GeneratorConfig generator();

    ReportConfig report();

    SmtpConfig smtp();

    AsciidoctorConfig asciidoctor();

    interface GeneratorConfig {
        @WithDefault("markdown")
        String engine();

        @WithDefault("./source")
        String source();

        @WithDefault("generated")
        String destination();

        Optional<String> image();

        Optional<String> css();
    }

    interface ReportConfig {
        Optional<String> author();

        Optional<String> title();

        Optional<String> email();

        @WithDefault("Cheers\n----\n{author}\n{title}")
        String signature();

        MailConfig mail();

        interface MailConfig {
            @WithDefault("{email}")
            String from();

            Optional<String> to();

            Optional<String> subject();
        }
    }

    interface SmtpConfig {
        Oauth2Config oauth2();

        interface Oauth2Config {
            Optional<String> clientId();

            Optional<String> clientSecret();

            Optional<String> refreshToken();
        }
    }

    interface AsciidoctorConfig {
        Map<String, String> attributes();

        Map<String, String> options();
    }
}
