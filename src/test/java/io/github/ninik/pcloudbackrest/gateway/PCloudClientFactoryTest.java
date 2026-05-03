package io.github.ninik.pcloudbackrest.gateway;

import com.pcloud.sdk.ApiClient;
import io.github.ninik.pcloudbackrest.config.Config;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PCloudClientFactoryTest {
    @Test
    void createsClientWithConfiguredApiHost() {
        Map<String, String> env = new HashMap<>();
        env.put("PCLOUD_ACCESS_TOKEN", "token");
        env.put("PCLOUD_REMOTE_FOLDER", "/Backups/Immich");
        env.put("LOCAL_BACKUP_FOLDER", "/data/photos");
        env.put("MODE", "backup");
        env.put("PCLOUD_API_HOST", "eapi.pcloud.com");

        ApiClient client = new PCloudClientFactory().create(Config.fromEnv(env));
        try {
            assertEquals("eapi.pcloud.com", client.apiHost());
        } finally {
            client.shutdown();
        }
    }
}
