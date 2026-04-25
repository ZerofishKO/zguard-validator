package io.github.ZeroFishKo.ZGuard.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class YmlConfigBindingTest {

    @Test
    void testYmlConfigBinding(@TempDir Path tempDir) throws Exception {
        String yamlContent = "validatorParams:\n" +
                "  paymentCode:\n" +
                "    codeLength: 8\n";
        Path ymlFile = tempDir.resolve("test.yml");
        // JDK 8 兼容写法
        Files.write(ymlFile, yamlContent.getBytes(StandardCharsets.UTF_8));

        YmlConfigBinder binder = new YmlConfigBinder();
        try (InputStream is = Files.newInputStream(ymlFile)) {
            binder.loadYml(is);
        }
        assertNotNull(binder.getGlobalYmlConfig().get("validatorParams"));
    }
}