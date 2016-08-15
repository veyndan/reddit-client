package com.veyndan.redditclient.api.network.interceptor;

import com.veyndan.redditclient.api.network.AccessToken;
import com.veyndan.redditclient.api.network.AuthenticationService;
import com.veyndan.redditclient.api.network.Credentials;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import rx.Observable;

public final class AccessTokenInterceptor implements Interceptor {

    private final AuthenticationService authenticationService;
    private final Credentials credentials;

    private AccessToken accessTokenCache;

    public AccessTokenInterceptor(AuthenticationService authenticationService, Credentials credentials) {
        this.authenticationService = authenticationService;
        this.credentials = credentials;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        final Request[] accessTokenRequest = {chain.request()};

        Observable.concat(accessTokenCache(), accessTokenNetwork())
                .first(accessToken -> !accessToken.isExpired())
                .subscribe(accessToken -> {
                    accessTokenRequest[0] = accessTokenRequest[0].newBuilder()
                            .header("Authorization", "Bearer " + accessToken.getAccessToken())
                            .build();
                });

        return chain.proceed(accessTokenRequest[0]);
    }

    private Observable<AccessToken> accessTokenCache() {
        if (accessTokenCache == null) {
            return Observable.empty();
        }
        return Observable.just(accessTokenCache);
    }

    private Observable<AccessToken> accessTokenNetwork() {
        Observable<AccessToken> observable = authenticationService.getAccessToken(
                "password", credentials.getUsername(), credentials.getPassword());

        // Save access token from network into the cache.
        return observable.doOnNext(accessToken -> accessTokenCache = accessToken);
    }
}
