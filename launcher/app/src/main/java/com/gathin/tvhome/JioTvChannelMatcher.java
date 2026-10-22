package com.gathin.tvhome;

/** Pure guard for identifying a JioTV Go channel card, not a menu button or category chip. */
final class JioTvChannelMatcher {
    private JioTvChannelMatcher() { }

    static boolean isChannelCard(boolean clickable, boolean longClickable,
                                 CharSequence imageDescription, CharSequence channelLabel) {
        if (!clickable || !longClickable || imageDescription == null || channelLabel == null) {
            return false;
        }
        String image = imageDescription.toString().trim();
        String label = channelLabel.toString().trim();
        return !label.isEmpty() && image.equals(label) && !"Favorite".equals(label)
                && !"Status".equals(label);
    }
}
