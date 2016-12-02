package com.veyndan.paper.reddit.api.reddit;

import android.text.TextUtils;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import com.veyndan.paper.reddit.api.reddit.model.Account2;
import com.veyndan.paper.reddit.api.reddit.model.CaptchaNew;
import com.veyndan.paper.reddit.api.reddit.model.Categories;
import com.veyndan.paper.reddit.api.reddit.model.Karma;
import com.veyndan.paper.reddit.api.reddit.model.Listing;
import com.veyndan.paper.reddit.api.reddit.model.MoreChildren;
import com.veyndan.paper.reddit.api.reddit.model.Prefs;
import com.veyndan.paper.reddit.api.reddit.model.RedditObject;
import com.veyndan.paper.reddit.api.reddit.model.Submission;
import com.veyndan.paper.reddit.api.reddit.model.Subreddit;
import com.veyndan.paper.reddit.api.reddit.model.Thing;
import com.veyndan.paper.reddit.api.reddit.model.Trophies;
import com.veyndan.paper.reddit.api.reddit.network.AboutSubreddit;
import com.veyndan.paper.reddit.api.reddit.network.AuthenticationService;
import com.veyndan.paper.reddit.api.reddit.network.Credentials;
import com.veyndan.paper.reddit.api.reddit.network.Message;
import com.veyndan.paper.reddit.api.reddit.network.MySubreddits;
import com.veyndan.paper.reddit.api.reddit.network.QueryBuilder;
import com.veyndan.paper.reddit.api.reddit.network.RedditService;
import com.veyndan.paper.reddit.api.reddit.network.Sort;
import com.veyndan.paper.reddit.api.reddit.network.SubredditSort;
import com.veyndan.paper.reddit.api.reddit.network.User;
import com.veyndan.paper.reddit.api.reddit.network.VoteDirection;
import com.veyndan.paper.reddit.api.reddit.network.interceptor.AccessTokenInterceptor;
import com.veyndan.paper.reddit.api.reddit.network.interceptor.AuthorizationInterceptor;
import com.veyndan.paper.reddit.api.reddit.network.interceptor.RawJsonInterceptor;
import com.veyndan.paper.reddit.api.reddit.network.interceptor.UserAgentInterceptor;

import java.util.Arrays;
import java.util.List;

import io.reactivex.Single;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class Reddit {

    private final RedditService redditService;

    public Reddit(final Credentials credentials) {
        final Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeAdapter(RedditObject.class, new RedditObjectDeserializer())
                .create();

        final GsonConverterFactory jsonConverterFactory = GsonConverterFactory.create(gson);

        final RxJava2CallAdapterFactory rxJava2CallAdapterFactory = RxJava2CallAdapterFactory.create();

        final OkHttpClient client = new OkHttpClient();

        final OkHttpClient.Builder authenticationClientBuilder = client.newBuilder()
                .addInterceptor(new UserAgentInterceptor(credentials.getUserAgent()))
                .addInterceptor(new AuthorizationInterceptor(credentials));

        final Retrofit authenticatorRetrofit = new Retrofit.Builder()
                .baseUrl("https://www.reddit.com")
                .addCallAdapterFactory(rxJava2CallAdapterFactory)
                .addConverterFactory(jsonConverterFactory)
                .client(authenticationClientBuilder.build())
                .build();

        final AuthenticationService authenticationService = authenticatorRetrofit.create(AuthenticationService.class);

        final OkHttpClient.Builder clientBuilder = client.newBuilder()
                .addInterceptor(new UserAgentInterceptor(credentials.getUserAgent()))
                .addInterceptor(new AccessTokenInterceptor(authenticationService, credentials))
                .addInterceptor(new RawJsonInterceptor());

        if (Config.DEBUG) {
            clientBuilder.addInterceptor(loggingInterceptor());
        }

        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://oauth.reddit.com/")
                .addCallAdapterFactory(rxJava2CallAdapterFactory)
                .addConverterFactory(jsonConverterFactory)
                .client(clientBuilder.build())
                .build();

        redditService = retrofit.create(RedditService.class);
    }

    private static HttpLoggingInterceptor loggingInterceptor() {
        final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        return loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
    }

    // ================================
    //             Account
    // ================================

    public Single<Response<Account2>> me() {
        return redditService.me();
    }

    public Single<Response<Thing<List<Karma>>>> myKarma() {
        return redditService.myKarma();
    }

    public Single<Response<Prefs>> myPrefs() {
        return redditService.myPrefs();
    }

    public Single<Response<Thing<Trophies>>> myTrophies() {
        return redditService.myTrophies();
    }

    // ================================
    //             Captcha
    // ================================

    public Single<Response<CaptchaNew>> newCaptcha() {
        return redditService.newCaptcha("json");
    }

    public Single<Response<Void>> idenCaptcha(final String iden) {
        return redditService.idenCaptcha(iden);
    }

    // ================================
    //         Links & Comments
    // ================================

    public Single<Response<Void>> hide(final String... ids) {
        return hide(Arrays.asList(ids));
    }

    public Single<Response<Void>> hide(final List<String> ids) {
        return redditService.hide(TextUtils.join(",", ids));
    }

    public Single<MoreChildren> moreChildren(final List<String> children, final String linkId) {
        return redditService.moreChildren("json", TextUtils.join(",", children), linkId);
    }

    public Single<Response<Void>> save(final String category, final String id) {
        return redditService.save(category, id);
    }

    public Single<Response<Categories>> savedCategories() {
        return redditService.savedCategories();
    }

    public Single<Response<Void>> unsave(final String id) {
        return redditService.unsave(id);
    }

    public Single<Response<Void>> vote(final VoteDirection voteDirection, final String id) {
        return redditService.vote(voteDirection, id);
    }

    // ================================
    //             Listings
    // ================================

    public Single<Response<Thing<Listing>>> subredditComments(final String subreddit, final String article) {
        return redditService.subredditComments(subreddit, article)
                .map(response -> {
                    final List<Thing<Listing>> things = response.body();
                    ((Submission) things.get(0).data.children.get(0)).getReplies().data.children.addAll(things.get(1).data.children);
                    return Response.success(things.get(0));
                });
    }

    public Single<Response<Thing<Listing>>> subreddit(final String subreddit, final Sort sort) {
        return subreddit(subreddit, sort, new QueryBuilder());
    }

    public Single<Response<Thing<Listing>>> subreddit(final String subreddit, final Sort sort,
                                                      final QueryBuilder query) {
        final Single<Response<Thing<Listing>>> request = redditService.subreddit(subreddit, sort, query.build());
        return paginate(request, query);
    }

    // ================================
    //         Private Messages
    // ================================

    public Single<Response<Thing<Listing>>> message(final Message message) {
        return redditService.message(message);
    }

    // ================================
    //            Subreddits
    // ================================

    public Single<Response<Thing<Listing>>> mySubreddits(final MySubreddits mySubreddits) {
        return redditService.mySubreddits(mySubreddits);
    }

    public Single<Response<Thing<Listing>>> subreddits(final SubredditSort sort) {
        return redditService.subreddits(sort);
    }

    public Single<Response<Thing<Subreddit>>> subredditAbout(final String subreddit) {
        return redditService.subredditAbout(subreddit);
    }

    // ================================
    //              Users
    // ================================

    public Single<Response<Thing<Listing>>> aboutSubreddit(
            final String subreddit, final AboutSubreddit where) {
        return redditService.aboutSubreddit(subreddit, where);
    }

    public Single<Response<Thing<Trophies>>> userTrophies(final String username) {
        return redditService.userTrophies(username);
    }

    public Single<Response<Thing<Account2>>> userAbout(final String username) {
        return redditService.userAbout(username);
    }

    public Single<Response<Thing<Listing>>> user(final String username, final User where) {
        return user(username, where, new QueryBuilder());
    }

    public Single<Response<Thing<Listing>>> user(final String username, final User where,
                                                 final QueryBuilder query) {
        final Single<Response<Thing<Listing>>> request = redditService.user(username, where, query.build());
        return paginate(request, query);
    }

    private static Single<Response<Thing<Listing>>> paginate(final Single<Response<Thing<Listing>>> page, final QueryBuilder query) {
        return Single.just(query)
                // TODO If the query has never been initialized, then we want it to pass.
                .filter(query1 -> !query1.build().containsKey("after") || query1.build().get("after") != null)
                .flatMapSingle(query1 -> page)
                .doOnSuccess(response -> query.after(response.body().data.after));
    }
}
