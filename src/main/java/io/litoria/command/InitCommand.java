package io.litoria.command;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import jakarta.inject.Inject;

import io.litoria.model.ProjectType;
import io.litoria.service.ProjectInitService;

@CommandDefinition(name = "init", description = "Create a new project with adoc or markdown files")
public class InitCommand implements Command<CommandInvocation> {

    @Option(shortName = 'f', description = "Force use of an existing folder", hasValue = false)
    private boolean force;

    @Option(shortName = 't', description = "Type of project: doc, report, slideshow",
            defaultValue = "doc")
    private String type;

    @Option(shortName = 'e', name = "engine",
            description = "Template engine: markdown or asciidoctor",
            defaultValue = "markdown")
    private String engine;

    @Option(shortName = 'l', name = "flavor",
            description = "Slideshow flavor: default or tokens",
            defaultValue = "default")
    private String flavor;

    @Argument(description = "Project directory path (defaults to current directory)")
    private String projectDir;

    @Inject
    ProjectInitService initService;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        ProjectType projectType = ProjectType.fromString(type);

        if (projectDir == null || projectDir.isBlank()) {
            invocation.println("Project directory path is required. Example: litoria init /tmp/my-project");
            return CommandResult.FAILURE;
        }

        boolean markdown = "markdown".equalsIgnoreCase(engine);
        invocation.println("Type selected: " + projectType.getValue());
        invocation.println("Engine: " + (markdown ? "markdown" : "asciidoctor"));

        boolean useTokens = "tokens".equalsIgnoreCase(flavor);

        try {
            initService.createProject(projectType, force, projectDir, markdown, useTokens);
            invocation.println("Project " + projectDir + " successfully created.");
            if (projectType == ProjectType.SLIDESHOW) {
                invocation.println("\nTo generate slides:");
                invocation.println("  litoria generate -r revealjs " + projectDir);
            }
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            invocation.println("Error: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }
}
