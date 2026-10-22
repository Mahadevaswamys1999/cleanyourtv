package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JioTvChannelMatcherTest {
    @Test public void identifiesClickableLongClickableChannelWithMatchingArtAndText() {
        assertTrue(JioTvChannelMatcher.isChannelCard(true, true, "TV9 Kannada", "TV9 Kannada"));
        assertTrue(JioTvChannelMatcher.isChannelCard(true, true, "Suvarna News", "Suvarna News"));
    }

    @Test public void ignoresTabsMenusLoadingScreensAndMismatchedNodes() {
        assertFalse(JioTvChannelMatcher.isChannelCard(true, false, "TV", "TV"));
        assertFalse(JioTvChannelMatcher.isChannelCard(false, true, "TV9 Kannada", "TV9 Kannada"));
        assertFalse(JioTvChannelMatcher.isChannelCard(true, true, "Filter Icon", "TV9 Kannada"));
        assertFalse(JioTvChannelMatcher.isChannelCard(true, true, null, "TV9 Kannada"));
        assertFalse(JioTvChannelMatcher.isChannelCard(true, true, "", ""));
        assertFalse(JioTvChannelMatcher.isChannelCard(true, true, "Favorite", "Favorite"));
    }
}
