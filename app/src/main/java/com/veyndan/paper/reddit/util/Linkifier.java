package com.veyndan.paper.reddit.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.View;

import com.veyndan.paper.reddit.MainActivity;
import com.veyndan.paper.reddit.api.reddit.Reddit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Linkifier {

    /**
     * https://www.reddit.com/r/modhelp/comments/1gd1at/name_rules_when_trying_to_create_a_subreddit/cajcylg
     */
    @NonNull private static final Pattern SUBREDDIT_PATTERN = Pattern.compile("[^\\w]/?[r|R]/([A-Za-z0-9]\\w{1,20})");

    /**
     * https://www.reddit.com/r/modhelp/comments/1gd1at/name_rules_when_trying_to_create_a_subreddit/cajcylg
     */
    @NonNull private static final Pattern USER_PATTERN = Pattern.compile("[^\\w]/?[u|U]/([A-Za-z0-9]\\w{1,20})");

    /**
     * https://support.twitter.com/articles/101299
     */
    @NonNull private static final Pattern TWITTER_MENTION_PATTERN = Pattern.compile("@(\\w{1,15})");

    public static void addLinks(@NonNull final Context context, @NonNull final Spannable spannable) {
        addSubredditLinks(context, spannable);
        addUserLinks(context, spannable);
        addTwitterMentionLinks(context, spannable);
    }

    private static void addSubredditLinks(@NonNull final Context context, @NonNull final Spannable spannable) {
        final Matcher matcher = SUBREDDIT_PATTERN.matcher(spannable);

        while (matcher.find()) {
            final String subredditName = matcher.group(1);

            spannable.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull final View view) {
                    final Intent subredditIntent = new Intent(context.getApplicationContext(), MainActivity.class);
                    subredditIntent.putExtra(Reddit.Filter.NODE_DEPTH, 0);
                    subredditIntent.putExtra(Reddit.Filter.SUBREDDIT_NAME, subredditName);
                    context.startActivity(subredditIntent);
                }
            }, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void addUserLinks(@NonNull final Context context, @NonNull final Spannable spannable) {
        // https://www.reddit.com/r/modhelp/comments/1gd1at/name_rules_when_trying_to_create_a_subreddit/cajcylg
        final Matcher matcher = USER_PATTERN.matcher(spannable);

        while (matcher.find()) {
            final String userName = matcher.group(1);

            spannable.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull final View view) {
                    final Intent profileIntent = new Intent(context.getApplicationContext(), MainActivity.class);
                    profileIntent.putExtra(Reddit.Filter.NODE_DEPTH, 0);
                    profileIntent.putExtra(Reddit.Filter.USER_NAME, userName);
                    profileIntent.putExtra(Reddit.Filter.USER_COMMENTS, true);
                    profileIntent.putExtra(Reddit.Filter.USER_SUBMITTED, true);
                    context.startActivity(profileIntent);
                }
            }, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void addTwitterMentionLinks(@NonNull final Context context, @NonNull final Spannable spannable) {
        final Matcher matcher = TWITTER_MENTION_PATTERN.matcher(spannable);

        while (matcher.find()) {
            final String twitterUsername = matcher.group(1);

            spannable.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull final View view) {
                    final String url = "https://twitter.com/" + twitterUsername;
                    final Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    context.startActivity(intent);
                }
            }, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
