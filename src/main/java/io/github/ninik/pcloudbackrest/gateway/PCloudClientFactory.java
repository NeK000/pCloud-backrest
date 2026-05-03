package io.github.ninik.pcloudbackrest.gateway;

import com.pcloud.sdk.ApiClient;
import com.pcloud.sdk.Authenticators;
import com.pcloud.sdk.PCloudSdk;
import io.github.ninik.pcloudbackrest.config.Config;

import java.util.concurrent.TimeUnit;

public final class PCloudClientFactory {
    public ApiClient create(Config config) {
        ApiClient.Builder builder = PCloudSdk.newClientBuilder()
                .authenticator(Authenticators.newOAuthAuthenticator(config.accessToken()))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(0, TimeUnit.SECONDS)
                .progressCallbackThreshold(32L * 1024L * 1024L);

        if (config.apiHost() != null) {
            builder.apiHost(config.apiHost());
        }
        return builder.create();
    }
}
