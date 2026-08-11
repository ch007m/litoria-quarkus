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

@CommandDefinition(name = "init", description = "Create a new AsciiDoc project")
public class InitCommand implements Command<CommandInvocation> {

    @Option(shortName = 'f', description = "Force use of an existing folder", hasValue = false)
    private boolean force;

    @Option(shortName = 't', description = "Type of project: simple, report, slideshow",
            defaultValue = "simple")
    private String type;

    @Argument(description = "Project directory path (defaults to current directory)")
    private String projectDir;

    @Inject
    ProjectInitService initService;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        ProjectType projectType = ProjectType.fromString(type);

        if (projectType == ProjectType.SLIDESHOW) {
            invocation.println("Slideshow project type will be supported in a future release.");
            return CommandResult.FAILURE;
        }

        if (projectDir == null || projectDir.isBlank()) {
            invocation.println("Project directory path is required. Example: litoria init /tmp/my-project");
            return CommandResult.FAILURE;
        }

        invocation.println("Type selected: " + projectType.getValue());

        try {
            initService.createProject(projectType, force, projectDir);
            invocation.println("Project " + projectDir + " successfully created.");
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            invocation.println("Error: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }
}
