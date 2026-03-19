package org.schabi.newpipe.extractor.services.youtube;

public class PoTokenResult {
    private final String visitorData;
    private final String playerPoToken;
    private final String streamingPoToken;

    public PoTokenResult(final String visitorData,
                         final String playerPoToken,
                         final String streamingPoToken) {
        this.visitorData = visitorData;
        this.playerPoToken = playerPoToken;
        this.streamingPoToken = streamingPoToken;
    }

    public String getVisitorData() {
        return visitorData;
    }

    public String getPlayerPoToken() {
        return playerPoToken;
    }

    public String getStreamingPoToken() {
        return streamingPoToken;
    }
}
