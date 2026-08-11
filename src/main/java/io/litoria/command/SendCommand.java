package io.litoria.command;

import java.util.Map;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import jakarta.inject.Inject;

import io.litoria.service.ConfigService;
import io.litoria.service.EmailService;

@CommandDefinition(name = "send", description = "Send HTML content as email via SMTP")
public class SendCommand implements Command<CommandInvocation> {

    @Option(shortName = 'f', name = "file",
            description = "Name of the HTML file to send (without extension)",
            defaultValue = "report")
    private String file;

    @Option(shortName = 'c', name = "config", description = "Config file to use")
    private String configFile;

    @Argument(description = "Project directory path")
    private String projectDir;

    @Inject
    EmailService emailService;

    @Inject
    ConfigService configService;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        try {
            String resolvedDir = configService.resolveProjectDir(projectDir);
            String cfgPath = configService.resolveConfigFile(resolvedDir, configFile);
            Map<String, Object> config = configService.loadConfig(cfgPath);

            invocation.println("Sending " + file + ".html ...");
            emailService.sendEmail(config, resolvedDir, file);
            invocation.println("Email sent successfully.");
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            invocation.println("Error: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }
}
