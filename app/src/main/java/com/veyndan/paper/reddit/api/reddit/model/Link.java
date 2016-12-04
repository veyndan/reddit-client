package com.veyndan.paper.reddit.api.reddit.model;

import com.google.common.collect.ImmutableList;

import java.util.regex.Pattern;

import io.reactivex.Maybe;
import okhttp3.HttpUrl;

public class Link extends Submission {

    private static final ImmutableList<String> DIRECT_IMAGE_DOMAINS = ImmutableList.of(
            "i.imgur.com", "i.redd.it", "i.reddituploads.com", "pbs.twimg.com",
            "upload.wikimedia.org");

    private boolean clicked;
    private String domain;
    private boolean hidden;
    private boolean isSelf;
    private String linkFlairCssClass;
    private String linkFlairText;
    private boolean locked;
    private Media media;
    private MediaEmbed mediaEmbed;
    private int numComments;
    private boolean over18;
    private String permalink;
    private String thumbnail;
    private Object suggestedSort;
    private Media secureMedia;
    private Object fromKind;
    private final Preview preview = new Preview();
    private MediaEmbed secureMediaEmbed;
    private PostHint postHint = PostHint.LINK;
    private Object from;
    private Object fromId;
    private boolean quarantine;
    private boolean visited;
    private Thing<Listing> replies = new Thing<>(new Listing());

    @Override
    public PostHint getPostHint() {
        if (isSelf) {
            postHint = PostHint.SELF;
        } else if (Pattern.compile("(.jpg|.jpeg|.gif|.png)$").matcher(linkUrl).find()
                || DIRECT_IMAGE_DOMAINS.contains(HttpUrl.parse(linkUrl).host())) {
            postHint = PostHint.IMAGE;
        }
        return postHint;
    }

    @Override
    public Object from() {
        return from;
    }

    @Override
    public Object fromId() {
        return fromId;
    }

    @Override
    public boolean quarantine() {
        return quarantine;
    }

    @Override
    public boolean visited() {
        return visited;
    }

    @Override
    public boolean isClicked() {
        return clicked;
    }

    @Override
    public Maybe<String> getDomain() {
        return Maybe.just(domain);
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    @Override
    public Maybe<String> getLinkFlairCssClass() {
        return Maybe.just(linkFlairCssClass);
    }

    @Override
    public Maybe<String> getLinkFlairText() {
        return linkFlairText == null ? Maybe.empty() : Maybe.just(linkFlairText);
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    public Maybe<Media> getMedia() {
        return Maybe.just(media);
    }

    @Override
    public Maybe<MediaEmbed> getMediaEmbed() {
        return Maybe.just(mediaEmbed);
    }

    @Override
    public Maybe<Integer> getNumComments() {
        return Maybe.just(numComments);
    }

    @Override
    public boolean isOver18() {
        return over18;
    }

    @Override
    public Maybe<String> getThumbnail() {
        return Maybe.just(thumbnail);
    }

    @Override
    public Maybe<Object> getSuggestedSort() {
        return Maybe.just(suggestedSort);
    }

    @Override
    public Maybe<Media> getSecureMedia() {
        return Maybe.just(secureMedia);
    }

    @Override
    public Object getFromKind() {
        return fromKind;
    }

    @Override
    public Preview getPreview() {
        return preview;
    }

    @Override
    public Maybe<MediaEmbed> getSecureMediaEmbed() {
        return Maybe.just(secureMediaEmbed);
    }

    @Override
    public Maybe<String> getParentId() {
        return Maybe.empty();
    }

    @Override
    public Thing<Listing> getReplies() {
        return replies;
    }

    @Override
    public String getLinkAuthor() {
        return author;
    }

    @Override
    public String getPermalink() {
        return "https://www.reddit.com" + permalink;
    }

    @Override
    public String getLinkId() {
        return id;
    }

    @Override
    public int getControversiality() {
        throw new UnsupportedOperationException("Method intention unknown");
    }

    @Override
    public boolean isHideable() {
        return true;
    }
}
