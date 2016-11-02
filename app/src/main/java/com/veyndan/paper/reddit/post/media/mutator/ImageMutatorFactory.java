package com.veyndan.paper.reddit.post.media.mutator;

import android.support.annotation.StringRes;
import android.util.Size;

import com.veyndan.paper.reddit.api.reddit.model.PostHint;
import com.veyndan.paper.reddit.api.reddit.model.Source;
import com.veyndan.paper.reddit.post.media.model.Image;
import com.veyndan.paper.reddit.post.model.Post;

import rx.Observable;

public final class ImageMutatorFactory implements MutatorFactory {

    public static ImageMutatorFactory create() {
        return new ImageMutatorFactory();
    }

    private ImageMutatorFactory() {
    }

    @Override
    public Observable<Post> mutate(final Post post) {
        return Observable.just(post)
                .filter(Post::isLink)
                .filter(post1 -> post1.getPostHint() == PostHint.IMAGE)
                .map(post1 -> {
                    final boolean imageDimensAvailable = !post.getPreview().images.isEmpty();

                    Size size = new Size(0, 0);
                    if (imageDimensAvailable) {
                        final Source source = post.getPreview().images.get(0).source;
                        size = new Size(source.width, source.height);
                    }

                    @StringRes final int type = post1.getLinkUrl().endsWith(".gif")
                            ? Image.IMAGE_TYPE_GIF
                            : Image.IMAGE_TYPE_STANDARD;
                    final Image image = new Image(post1.getLinkUrl(), size, type);
                    post1.getMedias().add(image);
                    return post1;
                });
    }
}
