package com.veyndan.paper.reddit.post.media.mutator;

import android.support.annotation.StringRes;
import android.util.Size;

import com.veyndan.paper.reddit.BuildConfig;
import com.veyndan.paper.reddit.api.imgur.network.ImgurService;
import com.veyndan.paper.reddit.api.reddit.model.PostHint;
import com.veyndan.paper.reddit.api.reddit.model.Source;
import com.veyndan.paper.reddit.post.media.model.Image;
import com.veyndan.paper.reddit.post.model.Post;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;

final class ImgurMutatorFactory implements MutatorFactory {

    private static final Pattern PATTERN = Pattern.compile("^https?://(?:m\\.|www\\.)?(i\\.)?imgur\\.com/(a/|gallery/)?(.*)$");

    static ImgurMutatorFactory create() {
        return new ImgurMutatorFactory();
    }

    private ImgurMutatorFactory() {
    }

    @Override
    public Maybe<Post> mutate(final Post post) {
        final Matcher matcher = PATTERN.matcher(post.linkUrl());

        return Single.just(post)
                .filter(post1 -> BuildConfig.HAS_IMGUR_API_CREDENTIALS && matcher.matches())
                .map(post1 -> {
                    final boolean isAlbum = matcher.group(2) != null;
                    final boolean isDirectImage = matcher.group(1) != null;

                    if (!isAlbum && !isDirectImage) {
                        // TODO .gifv links are HTML 5 videos so the PostHint should be set accordingly.
                        if (!post1.linkUrl().endsWith(".gifv")) {
                            post1 = post1
                                    .withLinkUrl(singleImageUrlToDirectImageUrl(post1.linkUrl()))
                                    .withPostHint(PostHint.IMAGE);
                        }
                    }

                    final Observable<Image> images;

                    if (isAlbum) {
                        post1 = post1.withPostHint(PostHint.IMAGE);

                        final OkHttpClient client = new OkHttpClient.Builder()
                                .addInterceptor(chain -> {
                                    Request request = chain.request().newBuilder()
                                            .addHeader("Authorization", "Client-ID " + BuildConfig.IMGUR_API_KEY)
                                            .build();
                                    return chain.proceed(request);
                                })
                                .build();

                        final Retrofit retrofit = new Retrofit.Builder()
                                .baseUrl("https://api.imgur.com/3/")
                                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                                .addConverterFactory(MoshiConverterFactory.create())
                                .client(client)
                                .build();

                        final ImgurService imgurService = retrofit.create(ImgurService.class);

                        final String id = matcher.group(3);

                        images = imgurService.album(id)
                                .flattenAsObservable(basic -> basic.body().getData().getImages())
                                .map(image -> Image.create(image.getLink(), new Size(image.getWidth(), image.getHeight())));
                    } else {
                        final boolean imageDimensAvailable = !post1.preview().images.isEmpty();

                        final String url = post1.linkUrl().endsWith(".gifv") && imageDimensAvailable
                                ? post1.preview().images.get(0).source.url
                                : post1.linkUrl();

                        final Size size;
                        if (imageDimensAvailable) {
                            final Source source = post1.preview().images.get(0).source;
                            size = new Size(source.width, source.height);
                        } else {
                            size = new Size(0, 0);
                        }

                        @StringRes final int type = post1.linkUrl().endsWith(".gif") || post1.linkUrl().endsWith(".gifv")
                                ? Image.IMAGE_TYPE_GIF
                                : Image.IMAGE_TYPE_STANDARD;

                        images = Observable.just(Image.create(url, size, type));
                    }

                    return post1.withMedias(post1.medias().value.concatWith(images));
                });
    }

    /**
     * Returns a direct image url
     * (e.g. <a href="http://i.imgur.com/1AGVxLl.png">http://i.imgur.com/1AGVxLl.png</a>) from a
     * single image url (e.g. <a href="http://imgur.com/1AGVxLl">http://imgur.com/1AGVxLl</a>)
     *
     * @param url The single image url.
     * @return The direct image url.
     */
    private static String singleImageUrlToDirectImageUrl(final String url) {
        return HttpUrl.parse(url).newBuilder().host("i.imgur.com").build() + ".png";
    }
}
