package com.houseofaje;

import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class HouseOfAjeApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(HouseOfAjeApplication.class);

        Environment env = app.run(args).getEnvironment();

        logApplicationStartup(env);
    }

    private static void logApplicationStartup(Environment env) {
        String protocol = Optional.ofNullable(env.getProperty("server.ssl.key-store"))
            .map(key -> "https")
            .orElse("http");

        String applicationName = env.getProperty("spring.application.name", "application");

        String serverPort = env.getProperty("server.port", "8080");

        String contextPath = Optional.ofNullable(
                env.getProperty("server.servlet.context-path")
            )
            .filter(StringUtils::isNotBlank)
            .orElse("/");

        String hostAddress = "localhost";

        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("The host name could not be determined, using `localhost` as fallback");
        }

        log.info(
            """

            ----------------------------------------------------------
            \tApplication '{}' is running! Access URLs:
            \tLocal:     {}://localhost:{}{}
            \tExternal:  {}://{}:{}{}
            \tProfile(s): {}
            ----------------------------------------------------------
            """,
            applicationName,
            protocol,
            serverPort,
            contextPath,
            protocol,
            hostAddress,
            serverPort,
            contextPath,
            env.getActiveProfiles().length == 0
                ? env.getDefaultProfiles()
                : env.getActiveProfiles()
        );
    }
}
