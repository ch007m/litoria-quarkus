package io.litoria;

import java.util.List;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.GroupCommand;
import org.aesh.command.invocation.CommandInvocation;


import io.litoria.command.GenerateCommand;
import io.litoria.command.InitCommand;
import io.litoria.command.SendCommand;

@CommandDefinition(
        name = "litoria",
        description = "Content management tool to generate HTML, PDF, and RevealJS slideshows from AsciiDoc or Markdown, and send reports via email",
        generateHelp = true,
        groupCommands = {
                InitCommand.class,
                GenerateCommand.class,
                SendCommand.class
        })
public class LitoriaCommand implements GroupCommand<CommandInvocation> {

    @Override
    public List<Command<CommandInvocation>> getCommands() {
        return List.of();
    }

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        invocation.println(invocation.getHelpInfo("litoria"));
        return CommandResult.SUCCESS;
    }
}
