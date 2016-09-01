package com.veyndan.redditclient.post.media.mutator;

import com.veyndan.redditclient.api.reddit.model.Source;
import com.veyndan.redditclient.post.model.Post;
import com.veyndan.redditclient.post.media.model.LinkImage;

import rx.Observable;

final class LinkImageMutatorFactory implements MutatorFactory {

    static LinkImageMutatorFactory create() {
        return new LinkImageMutatorFactory();
    }

    private LinkImageMutatorFactory() {
    }

    @Override
    public Observable<Post> mutate(final Post post) {
        return Observable.just(post)
                .filter(Post::isLink)
                .filter(post1 -> !post1.getPreview().images.isEmpty())
                .map(post1 -> {
                    final Source source = post1.getPreview().images.get(0).source;
                    final LinkImage linkImage = new LinkImage(source.url, post1.getDomain());
                    post1.setMediaObservable(Observable.just(linkImage));
                    return post1;
                });
    }
}