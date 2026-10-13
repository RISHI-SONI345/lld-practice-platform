package com.lldpractice.lld_practice_platform.problem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlProblemRepositoryTest {
    @Test
    void shippedProblemsLoadWithEverythingFilledIn() {
        YamlProblemRepository repo = new YamlProblemRepository("classpath:problems/*.yml");
        assertThat(repo.all()).extracting(Problem::id).containsExactly("parking-lot", "elevator", "vending-machine");
        for (Problem p : repo.all()) {
            assertThat(p.requirements()).isNotEmpty();
            assertThat(p.entryTypes()).isNotEmpty();
            assertThat(p.skeleton()).isNotEmpty();
            assertThat(p.capabilities()).hasSizeGreaterThanOrEqualTo(4);
            assertThat(p.decisionPoints()).hasSize(3);
            assertThat(p.decisionPoints()).allSatisfy(d -> assertThat(d.approaches()).hasSizeGreaterThanOrEqualTo(2).doesNotHaveDuplicates());
            assertThat(p.changeRequest().text()).isNotBlank();
        }
        assertThat(repo.find("parking-lot").get().capabilities())
                .filteredOn(Capability::watchLiterals).extracting(Capability::id)
                .containsExactlyInAnyOrder("compute-fee", "track-capacity");
        assertThat(repo.find("nope")).isEmpty();
    }

    @Test
    void shippedSkeletonsParseAndContainTheEntryTypes() {
        var parser = new com.lldpractice.lld_practice_platform.parser.javacode.JavaCodeParser();
        YamlProblemRepository repo = new YamlProblemRepository("classpath:problems/*.yml");
        for (Problem p : repo.all()) {
            var result = parser.parse(new com.lldpractice.lld_practice_platform.model.Submission(
                    "java", p.skeleton(), "", null));
            assertThat(result.issues()).as(p.id()).isEmpty();
            p.entryTypes().forEach(t -> assertThat(result.model().find(t)).as(p.id() + " " + t).isPresent());
        }
    }

    @Test
    void missingFieldFailsFastNamingFileAndField(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("bad.yml"), "id: x\ntitle: T\n");
        assertThatThrownBy(() -> new YamlProblemRepository("file:" + dir.toString().replace('\\', '/') + "/*.yml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bad.yml")
                .hasMessageContaining("statement");
    }

    @Test
    void invalidYamlFailsFast(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("broken.yml"), "id: [unclosed\n");
        assertThatThrownBy(() -> new YamlProblemRepository("file:" + dir.toString().replace('\\', '/') + "/*.yml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broken.yml");
    }

    @Test
    void noFilesAtAllFailsFast(@TempDir Path dir) {
        assertThatThrownBy(() -> new YamlProblemRepository("file:" + dir.toString().replace('\\', '/') + "/*.yml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No problem files");
    }
}
