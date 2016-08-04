package com.veyndan.redditclient;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsCallback;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsServiceConnection;
import android.support.customtabs.CustomTabsSession;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.LineHeightSpan;
import android.text.style.TextAppearanceSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.binaryfork.spanny.Spanny;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.common.base.Optional;
import com.jakewharton.rxbinding.support.design.widget.RxSnackbar;
import com.jakewharton.rxbinding.view.RxView;
import com.jakewharton.rxbinding.widget.RxCompoundButton;
import com.jakewharton.rxbinding.widget.RxPopupMenu;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.models.Tweet;
import com.twitter.sdk.android.tweetui.TweetUtils;
import com.twitter.sdk.android.tweetui.TweetView;

import org.apache.commons.lang3.StringEscapeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import rawjava.Reddit;
import rawjava.model.Comment;
import rawjava.model.Image;
import rawjava.model.Link;
import rawjava.model.PostHint;
import rawjava.model.RedditObject;
import rawjava.model.Source;
import rawjava.model.Submission;
import rawjava.network.VoteDirection;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    private static final int TYPE_SELF = 0x0;
    private static final int TYPE_IMAGE = 0x1;
    private static final int TYPE_ALBUM = 0x2;
    private static final int TYPE_LINK = 0x3;
    private static final int TYPE_LINK_IMAGE = 0x4;
    private static final int TYPE_TWEET = 0x5;
    private static final int TYPE_COMMENT = 0x6;

    private static final String CUSTOM_TAB_PACKAGE_NAME = "com.android.chrome";

    private final Activity activity;
    private final List<RedditObject> posts;
    private final Reddit reddit;
    private final int width;

    private final CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
    private final CustomTabsIntent customTabsIntent = builder.build();
    @Nullable private CustomTabsClient customTabsClient;

    public PostAdapter(Activity activity, List<RedditObject> posts, Reddit reddit, int width) {
        this.activity = activity;
        this.posts = posts;
        this.reddit = reddit;
        this.width = width;

        CustomTabsClient.bindCustomTabsService(activity, CUSTOM_TAB_PACKAGE_NAME, new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(ComponentName name, CustomTabsClient client) {
                // customTabsClient is now valid.
                customTabsClient = client;
                customTabsClient.warmup(0);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // customTabsClient is no longer valid. This also invalidates sessions.
                customTabsClient = null;
            }
        });
    }

    @Override
    public PostViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        View v = inflater.inflate(R.layout.post_item_link, parent, false);
        ViewStub mediaStub = (ViewStub) v.findViewById(R.id.post_media_stub);

        switch (viewType % 16) {
            case TYPE_SELF:
                break;
            case TYPE_IMAGE:
                mediaStub.setLayoutResource(R.layout.post_media_image);
                mediaStub.inflate();
                break;
            case TYPE_ALBUM:
                mediaStub.setLayoutResource(R.layout.post_media_album);
                mediaStub.inflate();
                break;
            case TYPE_LINK:
                mediaStub.setLayoutResource(R.layout.post_media_link);
                mediaStub.inflate();
                break;
            case TYPE_LINK_IMAGE:
                mediaStub.setLayoutResource(R.layout.post_media_link_image);
                mediaStub.inflate();
                break;
            case TYPE_TWEET:
                mediaStub.setLayoutResource(R.layout.post_media_tweet);
                mediaStub.inflate();
                break;
            case TYPE_COMMENT:
                v = inflater.inflate(R.layout.post_item_comment, parent, false);
                return new PostCommentViewHolder(v);
            default:
                throw new IllegalStateException("Unknown viewType: " + viewType);
        }

        return new PostLinkViewHolder(v);
    }

    private CharSequence trimTrailingWhitespace(@NonNull CharSequence source) {
        int i = source.length();

        // loop back to the first non-whitespace character
        do {
            i--;
        } while (i > 0 && Character.isWhitespace(source.charAt(i)));

        return source.subSequence(0, i + 1);
    }

    @Override
    public void onBindViewHolder(final PostViewHolder holder, int position) {
        final Context context = holder.itemView.getContext();
        final Submission submission = (Submission) posts.get(position);

        CharSequence age = DateUtils.getRelativeTimeSpanString(
                TimeUnit.SECONDS.toMillis(submission.createdUtc), System.currentTimeMillis(),
                DateUtils.SECOND_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_NO_NOON | DateUtils.FORMAT_NO_MIDNIGHT | DateUtils.FORMAT_NO_MONTH_DAY);

        int viewType = holder.getItemViewType();

        final List<Flair> flairs = new ArrayList<>();

        if (submission.stickied) {
            flairs.add(new Flair.Builder(ContextCompat.getColor(context, R.color.post_flair_stickied))
                    .text(context.getString(R.string.post_stickied))
                    .build());
        }

        if (submission instanceof Link && ((Link) submission).over18) {
            flairs.add(new Flair.Builder(ContextCompat.getColor(context, R.color.post_flair_nsfw))
                    .text(context.getString(R.string.post_nsfw))
                    .build());
        }

        if (submission instanceof Link && !TextUtils.isEmpty(((Link) submission).linkFlairText)) {
            flairs.add(new Flair.Builder(ContextCompat.getColor(context, R.color.post_flair_link))
                    .text(((Link) submission).linkFlairText)
                    .build());
        }

        if (submission.gilded != 0) {
            flairs.add(new Flair.Builder(ContextCompat.getColor(context, R.color.post_flair_gilded))
                    .text(String.valueOf(submission.gilded))
                    .icon(R.drawable.ic_star_white_12sp)
                    .build());
        }

        final String points = submission.scoreHidden
                ? context.getString(R.string.score_hidden)
                : context.getResources().getQuantityString(R.plurals.points, submission.score, submission.score);

        holder.setHeader(submission.linkTitle, context.getString(R.string.subtitle, submission.author, age, submission.subreddit), flairs);

        if (submission instanceof Comment) {
            final Comment comment = (Comment) posts.get(position);
            final PostCommentViewHolder commentHolder = (PostCommentViewHolder) holder;

            commentHolder.commentText.setText(trimTrailingWhitespace(Html.fromHtml(StringEscapeUtils.unescapeHtml4(comment.bodyHtml))));
            commentHolder.commentText.setMovementMethod(LinkMovementMethod.getInstance());
        } else if (submission instanceof Link) {
            final Link link = (Link) posts.get(position);
            final PostLinkViewHolder linkHolder = (PostLinkViewHolder) holder;

            switch (viewType % 16) {
                case TYPE_SELF:
                    break;
                case TYPE_IMAGE:
                    assert linkHolder.mediaContainer != null;
                    assert linkHolder.mediaImage != null;
                    assert linkHolder.mediaImageProgress != null;

                    linkHolder.mediaImageProgress.setVisibility(View.VISIBLE);

                    if (customTabsClient != null) {
                        CustomTabsSession session = customTabsClient.newSession(new CustomTabsCallback());
                        session.mayLaunchUrl(Uri.parse(submission.linkUrl), null, null);
                    }

                    RxView.clicks(linkHolder.mediaContainer)
                            .subscribe(aVoid -> {
                                customTabsIntent.launchUrl(activity, Uri.parse(submission.linkUrl));
                            });

                    final boolean imageDimensAvailable = !link.preview.images.isEmpty();

                    Glide.with(context)
                            .load(submission.linkUrl)
                            .listener(new RequestListener<String, GlideDrawable>() {
                                @Override
                                public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
                                    linkHolder.mediaImageProgress.setVisibility(View.GONE);
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                                    linkHolder.mediaImageProgress.setVisibility(View.GONE);
                                    if (!imageDimensAvailable) {
                                        final Image image = new Image();
                                        image.source.width = resource.getIntrinsicWidth();
                                        image.source.height = resource.getIntrinsicHeight();
                                        link.preview.images.add(image);

                                        linkHolder.mediaImage.getLayoutParams().height = (int) ((float) width / image.source.width * image.source.height);
                                    }
                                    return false;
                                }
                            })
                            .into(linkHolder.mediaImage);
                    if (imageDimensAvailable) {
                        Source source = link.preview.images.get(0).source;
                        linkHolder.mediaImage.getLayoutParams().height = (int) ((float) width / source.width * source.height);
                    }
                    break;
                case TYPE_ALBUM:
                    assert linkHolder.mediaContainer != null;

                    RecyclerView recyclerView = (RecyclerView) linkHolder.mediaContainer;

                    RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(context);
                    recyclerView.setLayoutManager(layoutManager);

                    final List<com.veyndan.redditclient.Image> images = new ArrayList<>();

                    final AlbumAdapter albumAdapter = new AlbumAdapter(activity, images, width, customTabsClient, customTabsIntent);
                    recyclerView.setAdapter(albumAdapter);

                    OkHttpClient client = new OkHttpClient.Builder()
                            .addInterceptor(chain -> {
                                Request request = chain.request().newBuilder()
                                        .addHeader("Authorization", "Client-ID " + Config.IMGUR_CLIENT_ID)
                                        .build();
                                return chain.proceed(request);
                            })
                            .build();

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl("https://api.imgur.com/3/")
                            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                            .addConverterFactory(MoshiConverterFactory.create())
                            .client(client)
                            .build();

                    ImgurService imgurService = retrofit.create(ImgurService.class);

                    imgurService.album(submission.linkUrl.split("/a/")[1])
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(basic -> {
                                images.addAll(basic.data.images);
                                albumAdapter.notifyDataSetChanged();
                            });
                    break;
                case TYPE_TWEET:
                    assert linkHolder.mediaContainer != null;

                    final Pattern pattern = Pattern.compile("https://twitter.com/\\w*/status/(\\d+)$");
                    final Matcher matcher = pattern.matcher(submission.linkUrl);

                    if (matcher.find()) {
                        final Optional<Long> tweetId = UrlMatcher.Twitter.tweetId(submission.linkUrl);

                        if (tweetId.isPresent()) {
                            TweetUtils.loadTweet(tweetId.get(), new Callback<Tweet>() {
                                @Override
                                public void success(Result<Tweet> result) {
                                    ((TweetView) linkHolder.mediaContainer).setTweet(result.data);
                                }

                                @Override
                                public void failure(TwitterException exception) {
                                    Timber.e(exception, "Load Tweet failure");
                                }
                            });
                        } else {
                            // TODO Show default link view as tweetId couldn't be parsed.
                        }
                    }
                    break;
                case TYPE_LINK_IMAGE:
                    assert linkHolder.mediaImage != null;
                    assert linkHolder.mediaImageProgress != null;

                    linkHolder.mediaImageProgress.setVisibility(View.VISIBLE);

                    Source source = link.preview.images.get(0).source;
                    Glide.with(context)
                            .load(source.url)
                            .listener(new RequestListener<String, GlideDrawable>() {
                                @Override
                                public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
                                    linkHolder.mediaImageProgress.setVisibility(View.GONE);
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                                    linkHolder.mediaImageProgress.setVisibility(View.GONE);
                                    return false;
                                }
                            })
                            .into(linkHolder.mediaImage);
                case TYPE_LINK:
                    assert linkHolder.mediaContainer != null;
                    assert linkHolder.mediaUrl != null;

                    if (customTabsClient != null) {
                        CustomTabsSession session = customTabsClient.newSession(null);

                        session.mayLaunchUrl(Uri.parse(submission.linkUrl), null, null);
                    }

                    RxView.clicks(linkHolder.mediaContainer)
                            .subscribe(aVoid -> {
                                customTabsIntent.launchUrl(activity, Uri.parse(submission.linkUrl));
                            });

                    linkHolder.mediaUrl.setText(link.domain);
                    break;
            }

            final String comments = context.getResources().getQuantityString(R.plurals.comments, link.numComments, link.numComments);
            linkHolder.score.setText(context.getString(R.string.score, points, comments));

            VoteDirection likes = submission.getLikes();

            linkHolder.upvote.setChecked(likes.equals(VoteDirection.UPVOTE));
            RxCompoundButton.checkedChanges(linkHolder.upvote)
                    .skip(1)
                    .subscribe(isChecked -> {
                        // Ensure that downvote and upvote aren't checked at the same time.
                        if (isChecked) {
                            linkHolder.downvote.setChecked(false);
                        }

                        submission.setLikes(isChecked ? VoteDirection.UPVOTE : VoteDirection.UNVOTE);
                        if (!submission.archived) {
                            reddit.vote(isChecked ? VoteDirection.UPVOTE : VoteDirection.UNVOTE, submission.getFullname())
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe();

                            submission.score += isChecked ? 1 : -1;

                            final String points1 = submission.scoreHidden
                                    ? context.getResources().getQuantityString(R.plurals.points, submission.score, submission.score)
                                    : context.getString(R.string.score_hidden);
                            final String comments1 = context.getResources().getQuantityString(R.plurals.comments, link.numComments, link.numComments);
                            linkHolder.score.setText(context.getString(R.string.score, points1, comments1));
                        }
                    });

            linkHolder.downvote.setChecked(likes.equals(VoteDirection.DOWNVOTE));
            RxCompoundButton.checkedChanges(linkHolder.downvote)
                    .skip(1)
                    .subscribe(isChecked -> {
                        // Ensure that downvote and upvote aren't checked at the same time.
                        if (isChecked) {
                            linkHolder.upvote.setChecked(false);
                        }

                        submission.setLikes(isChecked ? VoteDirection.DOWNVOTE : VoteDirection.UNVOTE);
                        if (!submission.archived) {
                            reddit.vote(isChecked ? VoteDirection.DOWNVOTE : VoteDirection.UNVOTE, submission.getFullname())
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe();

                            submission.score += isChecked ? -1 : 1;

                            final String points1 = submission.scoreHidden
                                    ? context.getResources().getQuantityString(R.plurals.points, submission.score, submission.score)
                                    : context.getString(R.string.score_hidden);
                            final String comments1 = context.getResources().getQuantityString(R.plurals.comments, link.numComments, link.numComments);
                            linkHolder.score.setText(context.getString(R.string.score, points1, comments1));
                        }
                    });

            linkHolder.save.setChecked(submission.saved);
            RxCompoundButton.checkedChanges(linkHolder.save)
                    .skip(1)
                    .subscribe(isChecked -> {
                        submission.saved = isChecked;
                        if (isChecked) {
                            reddit.save("", submission.getFullname())
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe();
                        } else {
                            reddit.unsave(submission.getFullname())
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe();
                        }
                    });

            final PopupMenu otherMenu = new PopupMenu(context, linkHolder.other);
            otherMenu.getMenuInflater().inflate(R.menu.menu_post_other, otherMenu.getMenu());

            RxView.clicks(linkHolder.other)
                    .subscribe(aVoid -> otherMenu.show());

            RxPopupMenu.itemClicks(otherMenu)
                    .subscribe(menuItem -> {
                        final int adapterPosition = holder.getAdapterPosition();

                        switch (menuItem.getItemId()) {
                            case R.id.action_post_hide:
                                final View.OnClickListener undoClickListener = view -> {
                                    // If undo pressed, then don't follow through with request to hide
                                    // the post.
                                    posts.add(adapterPosition, submission);
                                    notifyItemInserted(adapterPosition);
                                };

                                Snackbar snackbar = Snackbar.make(holder.itemView, R.string.notify_post_hidden, Snackbar.LENGTH_LONG)
                                        .setAction(R.string.notify_post_hidden_undo, undoClickListener);

                                RxSnackbar.dismisses(snackbar)
                                        .subscribe(event -> {
                                            // If undo pressed, don't hide post.
                                            if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                                                // Chance to undo post hiding has gone, so follow through with
                                                // hiding network request.
                                                reddit.hide(submission.getFullname())
                                                        .subscribeOn(Schedulers.io())
                                                        .observeOn(AndroidSchedulers.mainThread())
                                                        .subscribe();
                                            }
                                        });

                                snackbar.show();

                                // Hide post from list, but make no network request yet. Outcome of the
                                // user's interaction with the snackbar handling will determine this.
                                posts.remove(adapterPosition);
                                notifyItemRemoved(adapterPosition);
                                break;
                            case R.id.action_post_share:
                                break;
                            case R.id.action_post_profile:
                                Intent intent = new Intent(context.getApplicationContext(), ProfileActivity.class);
                                intent.putExtra("username", submission.author);
                                context.startActivity(intent);
                                break;
                            case R.id.action_post_subreddit:
                                intent = new Intent(context.getApplicationContext(), MainActivity.class);
                                intent.putExtra("subreddit", submission.subreddit);
                                context.startActivity(intent);
                                break;
                            case R.id.action_post_browser:
                                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(submission.linkUrl));
                                context.startActivity(intent);
                                break;
                            case R.id.action_post_report:
                                break;
                        }
                    });
        }
    }

    @Override
    public int getItemViewType(int position) {
        Submission submission = (Submission) posts.get(position);

        int viewType;

        if (submission instanceof Link && submission.linkUrl.contains("imgur.com/") && !submission.linkUrl.contains("/a/") && !submission.linkUrl.contains("/gallery/") && !submission.linkUrl.contains("i.imgur.com")) {
            submission.linkUrl = submission.linkUrl.replace("imgur.com", "i.imgur.com");
            if (!submission.linkUrl.endsWith(".gifv")) {
                submission.linkUrl += ".png";
            }
            ((Link) submission).setPostHint(PostHint.IMAGE);
        }

        if (submission instanceof Comment) {
            viewType = TYPE_COMMENT;
        } else if (submission instanceof Link && ((Link) submission).getPostHint().equals(PostHint.SELF)) {
            viewType = TYPE_SELF;
        } else if (submission instanceof Link && UrlMatcher.Twitter.tweetId(submission.linkUrl).isPresent()) {
            viewType = TYPE_TWEET;
        } else if (submission instanceof Link && ((Link) submission).getPostHint().equals(PostHint.IMAGE)) {
            viewType = TYPE_IMAGE;
        } else if (submission instanceof Link && submission.linkUrl.contains("/a/")) {
            viewType = TYPE_ALBUM;
        } else if (submission instanceof Link && !((Link) submission).preview.images.isEmpty()) {
            viewType = TYPE_LINK_IMAGE;
        } else {
            viewType = TYPE_LINK;
        }

        return viewType;
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {

        private final Context context;

        @BindView(R.id.post_title) TextView title;

        public PostViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);

            context = itemView.getContext();
        }

        void setHeader(final String title, final String subtitle, @NonNull final List<Flair> flairs) {
            final TextAppearanceSpan titleTextAppearanceSpan = new TextAppearanceSpan(context, R.style.PostTitleTextAppearance);

            final TextAppearanceSpan subtitleTextAppearanceSpan = new TextAppearanceSpan(context, R.style.PostSubtitleTextAppearance);
            final LineHeightSpan subtitleLineHeightSpan = new LineHeightSpan.WithDensity() {
                @Override
                public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int v, Paint.FontMetricsInt fm, TextPaint paint) {
                    final int titleSubtitleSpacing = context.getResources().getDimensionPixelSize(R.dimen.post_title_subtitle_spacing);
                    fm.ascent -= titleSubtitleSpacing;
                    fm.top -= titleSubtitleSpacing;

                    final int subtitleFlairSpacing = context.getResources().getDimensionPixelSize(R.dimen.post_subtitle_flair_spacing);
                    fm.descent += subtitleFlairSpacing;
                    fm.bottom += subtitleFlairSpacing;
                }

                @Override
                public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int v, Paint.FontMetricsInt fm) {
                    chooseHeight(text, start, end, spanstartv, v, fm, null);
                }
            };

            final Spanny spanny = new Spanny(title, titleTextAppearanceSpan)
                    .append("\n")
                    .append(subtitle, subtitleTextAppearanceSpan, subtitleLineHeightSpan);

            if (!flairs.isEmpty()) {
                spanny.append("\n");

                final Spanny flairsSpanny = new Spanny();

                String divider = "";
                for (Flair flair : flairs) {
                    flairsSpanny.append(divider);
                    if (divider.isEmpty()) {
                        divider = "   "; // TODO Replace with margin left and right of 4dp
                    }

                    flairsSpanny.append(flair.getSpannable(context));
                }

                spanny.append(flairsSpanny, new LineHeightSpan.WithDensity() {
                    @Override
                    public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int v, Paint.FontMetricsInt fm, TextPaint paint) {
                        final int resetTitleSubtitleSpacing = context.getResources().getDimensionPixelSize(R.dimen.post_title_subtitle_spacing);
                        fm.ascent += resetTitleSubtitleSpacing;
                        fm.top += resetTitleSubtitleSpacing;

                        final int resetSubtitleFlairSpacing = context.getResources().getDimensionPixelSize(R.dimen.post_subtitle_flair_spacing);
                        fm.descent -= resetSubtitleFlairSpacing;
                        fm.bottom -= resetSubtitleFlairSpacing;
                    }

                    @Override
                    public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int v, Paint.FontMetricsInt fm) {
                        chooseHeight(text, start, end, spanstartv, v, fm, null);
                    }
                });
            }

            this.title.setText(spanny);
        }
    }

    public static class PostLinkViewHolder extends PostViewHolder {

        @BindView(R.id.post_score) TextView score;
        @BindView(R.id.post_upvote) ToggleButton upvote;
        @BindView(R.id.post_downvote) ToggleButton downvote;
        @BindView(R.id.post_save) ToggleButton save;
        @BindView(R.id.post_other) ImageButton other;

        // Media: Image
        // Media: Link
        // Media: Link Image
        @Nullable @BindView(R.id.post_media_container) View mediaContainer;

        // Media: Image
        // Media: Link Image
        @Nullable @BindView(R.id.post_media_image) ImageView mediaImage;

        // Media: Image
        // Media: Link Image
        @Nullable @BindView(R.id.post_media_image_progress) ProgressBar mediaImageProgress;

        // Media: Link
        // Media: Link Image
        @Nullable @BindView(R.id.post_media_url) TextView mediaUrl;

        public PostLinkViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }

    public static class PostCommentViewHolder extends PostViewHolder {

        @BindView(R.id.post_comment_text) TextView commentText;

        public PostCommentViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
