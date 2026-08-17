package io.litoria.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.litoria.model.ProjectType;
import io.quarkus.test.junit.QuarkusTest;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ProjectInitServiceTest {

    @Inject
    ProjectInitService initService;

    @Test
    void createMarkdownReportProjectCreatesCorrectStructure(@TempDir Path tempDir) throws IOException {
        Path projectDir = tempDir.resolve("my-report");

        initService.createProject(ProjectType.REPORT, false, projectDir.toString(), true);

        assertThat(projectDir.resolve("source")).isDirectory();
        assertThat(projectDir.resolve("source/css")).isDirectory();
        assertThat(projectDir.resolve("source/image")).isDirectory();
        assertThat(projectDir.resolve("source/report.md")).isRegularFile();
        assertThat(projectDir.resolve("config.yml")).doesNotExist();
        assertThat(projectDir.resolve("config.yaml")).doesNotExist();
    }

    @Test
    void createMarkdownProjectIncludesFrontmatterInTemplate(@TempDir Path tempDir) throws IOException {
        Path projectDir = tempDir.resolve("my-report");

        initService.createProject(ProjectType.REPORT, false, projectDir.toString(), true);

        String content = Files.readString(projectDir.resolve("source/report.md"));
        assertThat(content).startsWith("---");
        assertThat(content).contains("author:");
        assertThat(content).contains("title:");
        assertThat(content).contains("email:");
        assertThat(content).contains("to:");
        assertThat(content).contains("subject:");
        assertThat(content).contains("signature:");
    }

    @Test
    void createMarkdownProjectCopiesCssFiles(@TempDir Path tempDir) throws IOException {
        Path projectDir = tempDir.resolve("my-report");

        initService.createProject(ProjectType.REPORT, false, projectDir.toString(), true);

        assertThat(projectDir.resolve("source/css/report.css")).isRegularFile();
    }

    @Test
    void createMarkdownProjectCopiesImages(@TempDir Path tempDir) throws IOException {
        Path projectDir = tempDir.resolve("my-report");

        initService.createProject(ProjectType.REPORT, false, projectDir.toString(), true);

        assertThat(projectDir.resolve("source/image/quarkus-logo.png")).isRegularFile();
    }

    @Test
    void createAsciidoctorReportProjectCreatesCorrectStructure(@TempDir Path tempDir) throws IOException {
        Path projectDir = tempDir.resolve("my-adoc");

        initService.createProject(ProjectType.REPORT, false, projectDir.toString(), false);

        assertThat(projectDir.resolve("source")).isDirectory();
        assertThat(projectDir.resolve("source/css")).isDirectory();
        assertThat(projectDir.resolve("source/image")).isDirectory();
        assertThat(projectDir.resolve("source/report.adoc")).isRegularFile();
        assertThat(projectDir.resolve("source/minute.adoc")).isRegularFile();
        assertThat(projectDir.resolve("config.yml")).doesNotExist();
    }

    @Test
    void createProjectWithForceOverwritesExisting(@TempDir Path tempDir) throws IOException {
        Path projectDir = tempDir.resolve("existing");
        Files.createDirectories(projectDir.resolve("source"));
        Files.writeString(projectDir.resolve("source/old.md"), "old content");

        initService.createProject(ProjectType.REPORT, true, projectDir.toString(), true);

        assertThat(projectDir.resolve("source/report.md")).isRegularFile();
        assertThat(projectDir.resolve("source/old.md")).doesNotExist();
    }
}
