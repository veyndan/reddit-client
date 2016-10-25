package com.veyndan.paper.reddit;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import com.airbnb.deeplinkdispatch.DeepLink;
import com.veyndan.paper.reddit.api.reddit.model.Listing;
import com.veyndan.paper.reddit.api.reddit.model.Thing;
import com.veyndan.paper.reddit.api.reddit.network.QueryBuilder;
import com.veyndan.paper.reddit.api.reddit.network.Sort;
import com.veyndan.paper.reddit.api.reddit.network.TimePeriod;
import com.veyndan.paper.reddit.api.reddit.network.User;
import com.veyndan.paper.reddit.post.PostsFragment;
import com.veyndan.paper.reddit.post.model.Post;

import butterknife.ButterKnife;
import retrofit2.Response;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

@DeepLink({
        "http://reddit.com/u/{" + Filter.USER_NAME + '}',
        "http://reddit.com/user/{" + Filter.USER_NAME + '}'
})
public class MainActivity extends BaseActivity {

    private PostsFragment postsFragment;

    private String subreddit;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        postsFragment = (PostsFragment) getSupportFragmentManager().findFragmentById(R.id.main_fragment_posts);

        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();
        final String dataString = intent.getDataString();

        if (dataString != null) {
            if (dataString.contains(RedditProvider.ROOT_AUTHORITY + "/subreddit/")) {
                extras.putString(Filter.SUBREDDIT_NAME, dataString.substring(dataString.lastIndexOf('/') + 1));
            } else if (dataString.contains(RedditProvider.ROOT_AUTHORITY + "/user/")) {
                extras.putString(Filter.USER_NAME, dataString.substring(dataString.lastIndexOf('/') + 1));
                extras.putBoolean(Filter.USER_COMMENTS, true);
                extras.putBoolean(Filter.USER_SUBMITTED, true);
            }
        }

        final Observable<Response<Thing<Listing>>> defaultRequest = Request.subreddit("all", Sort.HOT);
        final Observable<Response<Thing<Listing>>> mergedFilters = mergeFilters(extras, defaultRequest);
        postsFragment.setRequest(mergedFilters);

        final PostsFragment commentsFragment = (PostsFragment) getSupportFragmentManager().findFragmentById(R.id.main_fragment_comments);

        EventBus.INSTANCE.toObserverable()
                .subscribeOn(Schedulers.io())
                .ofType(Post.class)
                .filter(post -> commentsFragment != null && commentsFragment.isVisible())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(post -> {
                    commentsFragment.setRequest(Request.comments(post.getSubreddit(), post.getArticle()));
                }, Timber::e);
    }

    @NonNull
    private Observable<Response<Thing<Listing>>> mergeFilters(@Nullable final Bundle bundle,
                                                              @NonNull final Observable<Response<Thing<Listing>>> defaultRequest) {
        if (bundle == null) {
            return defaultRequest;
        }

        final TimePeriod[] timePeriods = {
                TimePeriod.HOUR,
                TimePeriod.DAY,
                TimePeriod.WEEK,
                TimePeriod.MONTH,
                TimePeriod.YEAR,
                TimePeriod.ALL
        };

        final TimePeriod timePeriod = timePeriods[bundle.getInt(Filter.TIME_PERIOD_POSITION)];

        subreddit = bundle.getString(Filter.SUBREDDIT_NAME);

        final String username = bundle.getString(Filter.USER_NAME);
        final boolean comments = bundle.getBoolean(Filter.USER_COMMENTS);
        final boolean submitted = bundle.getBoolean(Filter.USER_SUBMITTED);
        final boolean gilded = bundle.getBoolean(Filter.USER_GILDED);

        if (TextUtils.isEmpty(subreddit) && TextUtils.isEmpty(username)) {
            return defaultRequest;
        } else if (!TextUtils.isEmpty(subreddit) && TextUtils.isEmpty(username)) {
            return Request.subreddit(subreddit, Sort.HOT, new QueryBuilder().t(timePeriod));
        } else if (TextUtils.isEmpty(subreddit)) { // && !TextUtils.isEmpty(username)
            final User user;
            if (bundle.getBoolean(DeepLink.IS_DEEP_LINK, false)) {
                user = User.OVERVIEW;
            } else if ((comments == submitted) && gilded) {
                user = User.GILDED;
            } else if ((comments != submitted) && gilded) {
                throw new UnsupportedOperationException("User state unsure");
            } else if (comments && submitted) {
                user = User.OVERVIEW;
            } else if (comments) {
                user = User.COMMENTS;
            } else if (submitted) {
                user = User.SUBMITTED;
            } else {
                throw new UnsupportedOperationException("User state unsure");
            }

            return Request.user(username, user, new QueryBuilder().t(timePeriod));
        } else { // !TextUtils.isEmpty(subreddit) && !TextUtils.isEmpty(username)
            // TODO Concatenate the subreddit and username search query
            return defaultRequest;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_account_add:
                final Intent intent = new Intent(this, AuthenticationActivity.class);
                startActivityForResult(intent, 0);
                return true;
            case R.id.action_filter:
                final FragmentManager fragmentManager = getSupportFragmentManager();
                final FilterFragment filterFragment = FilterFragment.newInstance();
                filterFragment.show(fragmentManager, "fragment_filter");
                return true;
            case R.id.action_sort_hot:
                postsFragment.setRequest(Request.subreddit(subreddit, Sort.HOT));
                return true;
            case R.id.action_sort_new:
                postsFragment.setRequest(Request.subreddit(subreddit, Sort.NEW));
                return true;
            case R.id.action_sort_rising:
                postsFragment.setRequest(Request.subreddit(subreddit, Sort.RISING));
                return true;
            case R.id.action_sort_controversial:
                postsFragment.setRequest(Request.subreddit(subreddit, Sort.CONTROVERSIAL));
                return true;
            case R.id.action_sort_top:
                postsFragment.setRequest(Request.subreddit(subreddit, Sort.TOP));
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            final String code = data.getStringExtra("code");
        }
    }
}