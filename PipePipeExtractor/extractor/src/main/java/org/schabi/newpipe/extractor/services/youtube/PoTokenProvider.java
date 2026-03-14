package org.schabi.newpipe.extractor.services.youtube;

import javax.annotation.Nullable;

public interface PoTokenProvider {
    @Nullable
    PoTokenResult getWebClientPoToken(String videoId);

    @Nullable
    PoTokenResult getWebEmbedClientPoToken(String videoId);

    @Nullable
    PoTokenResult getAndroidClientPoToken(String videoId);

    @Nullable
    PoTokenResult getIosClientPoToken(String videoId);
}
