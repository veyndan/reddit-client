package com.veyndan.redditclient.post.model.media;

public class Link {

    private final String domain;

    public Link(final String domain) {
        this.domain = domain;
    }

    public String getDomain() {
        return domain;
    }
}
