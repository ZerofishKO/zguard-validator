package io.github.ZeroFishKoZGuard.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class YmlConfigBindingTest {

    @Test
    void testYmlConfigBinding(@TempDir Path tempDir) throws Exception {
        String yamlContent = """
                validatorParams:
                  paymentCode:
                    codeLength: 8
                """;
        Path ymlFile = tempDir.resolve("test.yml");
        Files.writeString(ymlFile, yamlContent);

        YmlConfigBinder binder = new YmlConfigBinder();
        try (InputStream is = Files.newInputStream(ymlFile)) {
            binder.loadYml(is);
        }
        assertNotNull(binder.getGlobalYmlConfig().get("validatorParams"));
    }
}