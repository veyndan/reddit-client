package com.veyndan.redditclient.api;

import com.google.common.base.Joiner;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.veyndan.redditclient.api.model.Account2;
import com.veyndan.redditclient.api.model.CaptchaNew;
import com.veyndan.redditclient.api.model.Categories;
import com.veyndan.redditclient.api.model.Comment;
import com.veyndan.redditclient.api.model.Karma;
import com.veyndan.redditclient.api.model.Listing;
import com.veyndan.redditclient.api.model.Prefs;
import com.veyndan.redditclient.api.model.RedditObject;
import com.veyndan.redditclient.api.model.Subreddit;
import com.veyndan.redditclient.api.model.Thing;
import com.veyndan.redditclient.api.model.Trophies;
import com.veyndan.redditclient.api.network.AboutSubreddit;
import com.veyndan.redditclient.api.network.AuthenticationService;
import com.veyndan.redditclient.api.network.Credentials;
import com.veyndan.redditclient.api.network.Message;
import com.veyndan.redditclient.api.network.MySubreddits;
import com.veyndan.redditclient.api.network.QueryBuilder;
import com.veyndan.redditclient.api.network.RedditService;
import com.veyndan.redditclient.api.network.Sort;
import com.veyndan.redditclient.api.network.SubredditSort;
import com.veyndan.redditclient.api.network.User;
import com.veyndan.redditclient.api.network.VoteDirection;
import com.veyndan.redditclient.api.network.interceptor.AccessTokenInterceptor;
import com.veyndan.redditclient.api.network.interceptor.AuthorizationInterceptor;
import com.veyndan.redditclient.api.network.interceptor.RawJsonInterceptor;
import com.veyndan.redditclient.api.network.interceptor.UserAgentInterceptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;

public class Reddit {

    private final RedditService redditService;

    private Reddit(final Builder builder) {
        final Credentials credentials = builder.credentials;

        final Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeAdapter(RedditObject.class, new RedditObjectDeserializer())
                .registerTypeAdapter(Comment.class, new CommentDeserializer())
                .create();

        final OkHttpClient.Builder authenticationClientBuilder = new OkHttpClient.Builder()
                .addInterceptor(new UserAgentInterceptor(credentials.getUserAgent()))
                .addInterceptor(new AuthorizationInterceptor(credentials));

        for (Interceptor interceptor : builder.networkInterceptors) {
            authenticationClientBuilder.addNetworkInterceptor(interceptor);
        }

        final Retrofit authenticatorRetrofit = new Retrofit.Builder()
                .baseUrl("https://www.reddit.com")
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(authenticationClientBuilder.build())
                .build();

        final AuthenticationService authenticationService = authenticatorRetrofit.create(AuthenticationService.class);

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .addInterceptor(new UserAgentInterceptor(credentials.getUserAgent()))
                .addInterceptor(new AccessTokenInterceptor(authenticationService, credentials))
                .addInterceptor(new RawJsonInterceptor());

        if (com.veyndan.redditclient.api.Config.DEBUG) clientBuilder.addInterceptor(loggingInterceptor());

        for (Interceptor interceptor : builder.networkInterceptors) {
            clientBuilder.addNetworkInterceptor(interceptor);
        }

        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://oauth.reddit.com/")
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(clientBuilder.build())
                .build();

        redditService = retrofit.create(RedditService.class);
    }

    private static HttpLoggingInterceptor loggingInterceptor() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        return loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
    }

    // ================================
    //             Account
    // ================================

    public Observable<Response<Account2>> me() {
        return redditService.me();
    }

    public Observable<Response<Thing<List<Karma>>>> myKarma() {
        return redditService.myKarma();
    }

    public Observable<Response<Prefs>> myPrefs() {
        return redditService.myPrefs();
    }

    public Observable<Response<Thing<Trophies>>> myTrophies() {
        return redditService.myTrophies();
    }

    // ================================
    //             Captcha
    // ================================

    public Observable<Response<CaptchaNew>> newCaptcha() {
        return redditService.newCaptcha("json");
    }

    public Observable<Response<ResponseBody>> idenCaptcha(final String iden) {
        return redditService.idenCaptcha(iden);
    }

    // ================================
    //         Links & Comments
    // ================================

    public Observable<Response<ResponseBody>> hide(String... ids) {
        return hide(Arrays.asList(ids));
    }

    public Observable<Response<ResponseBody>> hide(List<String> ids) {
        return redditService.hide(Joiner.on(',').join(ids));
    }

    public Observable<Response<ResponseBody>> save(final String category, final String id) {
        return redditService.save(category, id);
    }

    public Observable<Response<Categories>> savedCategories() {
        return redditService.savedCategories();
    }

    public Observable<Response<ResponseBody>> unsave(final String id) {
        return redditService.unsave(id);
    }

    public Observable<Response<ResponseBody>> vote(final VoteDirection voteDirection, final String id) {
        return redditService.vote(voteDirection, id);
    }

    // ================================
    //             Listings
    // ================================

    public Observable<Response<List<Thing<Listing>>>> subredditComments(String subreddit, String article) {
        return redditService.subredditComments(subreddit, article);
    }

    public Observable<Response<Thing<Listing>>> subreddit(
            String subreddit, Sort sort, Observable<Boolean> trigger,
            Scheduler subscribeOn, Scheduler observeOn) {
        return subreddit(subreddit, sort, new QueryBuilder(), trigger, subscribeOn, observeOn);
    }

    public Observable<Response<Thing<Listing>>> subreddit(
            final String subreddit, final Sort sort, final QueryBuilder queryBuilder,
            final Observable<Boolean> trigger,
            final Scheduler subscribeOn, final Scheduler observeOn) {
        return redditService.subreddit(subreddit, sort, queryBuilder.build())
                .subscribeOn(subscribeOn)
                .observeOn(observeOn)
                .flatMap(new Func1<Response<Thing<Listing>>, Observable<Response<Thing<Listing>>>>() {
                    @Override
                    public Observable<Response<Thing<Listing>>> call(final Response<Thing<Listing>> response) {
                        return Observable.just(response)
                                .concatWith(trigger
                                        .filter(aBoolean -> aBoolean)
                                        .flatMap(new Func1<Boolean, Observable<Response<Thing<Listing>>>>() {
                                            @Override
                                            public Observable<Response<Thing<Listing>>> call(Boolean aBoolean) {
                                                return subreddit(subreddit, sort, queryBuilder.after(response.body().data.after), trigger, subscribeOn, observeOn);
                                            }
                                        }));
                    }
                });
    }

    // ================================
    //         Private Messages
    // ================================

    public Observable<Response<Thing<Listing>>> message(final Message message) {
        return redditService.message(message);
    }

    // ================================
    //            Subreddits
    // ================================

    public Observable<Response<Thing<Listing>>> mySubreddits(final MySubreddits mySubreddits) {
        return redditService.mySubreddits(mySubreddits);
    }

    public Observable<Response<Thing<Listing>>> subreddits(final SubredditSort sort) {
        return redditService.subreddits(sort);
    }

    public Observable<Response<Thing<Subreddit>>> subredditAbout(final String subreddit) {
        return redditService.subredditAbout(subreddit);
    }

    // ================================
    //              Users
    // ================================

    public Observable<Response<Thing<Listing>>> aboutSubreddit(
            final String subreddit, final AboutSubreddit where) {
        return redditService.aboutSubreddit(subreddit, where);
    }

    public Observable<Response<Thing<Trophies>>> userTrophies(final String username) {
        return redditService.userTrophies(username);
    }

    public Observable<Response<Thing<Account2>>> userAbout(final String username) {
        return redditService.userAbout(username);
    }

    public Observable<Response<Thing<Listing>>> user(final String username, final User where) {
        return redditService.user(username, where);
    }

    public static class Builder {

        private final Credentials credentials;
        private final List<Interceptor> networkInterceptors = new ArrayList<>();

        public Builder(final Credentials credentials) {
            this.credentials = credentials;
        }

        public Builder addNetworkInterceptor(Interceptor interceptor) {
            networkInterceptors.add(interceptor);
            return this;
        }

        public Reddit build() {
            return new Reddit(this);
        }
    }
}
