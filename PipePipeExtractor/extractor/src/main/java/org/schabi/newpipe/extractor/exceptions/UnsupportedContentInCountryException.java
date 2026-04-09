package org.schabi.newpipe.extractor.exceptions;

public class UnsupportedContentInCountryException extends ContentNotAvailableException {
    public UnsupportedContentInCountryException(final String message) {
        super(message);
    }
}
