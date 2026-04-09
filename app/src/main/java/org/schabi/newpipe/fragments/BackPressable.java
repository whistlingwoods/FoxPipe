package org.schabi.newpipe.fragments;

/**
 * Indicates that the current fragment can handle back presses.
 */
public interface BackPressable {
    /**
     * @return whether this fragment should intercept the next back press
     */
    default boolean canHandleBackPress() {
        return true;
    }

    /**
     * A back press was delegated to this fragment.
     *
     * @return if the back press was handled
     */
    boolean onBackPressed();
}
