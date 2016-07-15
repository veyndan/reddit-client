package com.veyndan.redditclient;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
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

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import rawjava.Reddit;
import rawjava.model.Image;
import rawjava.model.Link;
import rawjava.model.PostHint;
import rawjava.model.Source;
import rawjava.model.Thing;
import rawjava.network.VoteDirection;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    private static final String TAG = "veyndan_PostAdapter";

    private static final int TYPE_SELF = 0x0;
    private static final int TYPE_IMAGE = 0x1;
    private static final int TYPE_ALBUM = 0x2;
    private static final int TYPE_LINK = 0x3;
    private static final int TYPE_LINK_IMAGE = 0x4;
    private static final int TYPE_TWEET = 0x5;

    private static final int TYPE_FLAIR = 0x10;

    private final List<Thing<Link>> posts;
    private final Reddit reddit;
    private final int width;

    public PostAdapter(List<Thing<Link>> posts, Reddit reddit, int width) {
        this.posts = posts;
        this.reddit = reddit;
        this.width = width;
    }

    @Override
    public PostViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        View v = inflater.inflate(R.layout.post_item, parent, false);
        ViewStub flairStub = (ViewStub) v.findViewById(R.id.post_flair_stub);
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
            default:
                throw new IllegalStateException("Unknown viewType: " + viewType);
        }

        if ((viewType & TYPE_FLAIR) != 0) {
            flairStub.inflate();
        }

        return new PostViewHolder(v);
    }

    @Override
    public void onBindViewHolder(final PostViewHolder holder, int position) {
        final Thing<Link> post = posts.get(position);
        final Context context = holder.itemView.getContext();

        holder.title.setText(post.data.title);

        CharSequence age = DateUtils.getRelativeTimeSpanString(
                TimeUnit.SECONDS.toMillis(post.data.createdUtc), System.currentTimeMillis(),
                DateUtils.SECOND_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_NO_NOON | DateUtils.FORMAT_NO_MIDNIGHT | DateUtils.FORMAT_NO_MONTH_DAY);

        holder.subtitle.setText(context.getString(R.string.subtitle, post.data.author, age, post.data.subreddit));

        int viewType = holder.getItemViewType();

        switch (viewType % 16) {
            case TYPE_SELF:
                break;
            case TYPE_IMAGE:
                assert holder.mediaContainer != null;
                assert holder.mediaImage != null;
                assert holder.mediaImageProgress != null;

                holder.mediaImageProgress.setVisibility(View.VISIBLE);

                RxView.clicks(holder.mediaContainer)
                        .subscribe(aVoid -> {
                            Thing<Link> post1 = posts.get(holder.getAdapterPosition());
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(post1.data.url));
                            context.startActivity(intent);
                        });

                final boolean imageDimensAvailable = !post.data.preview.images.isEmpty();

                Glide.with(context)
                        .load(post.data.url)
                        .listener(new RequestListener<String, GlideDrawable>() {
                            @Override
                            public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
                                holder.mediaImageProgress.setVisibility(View.GONE);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                                holder.mediaImageProgress.setVisibility(View.GONE);
                                if (!imageDimensAvailable) {
                                    final Image image = new Image();
                                    image.source = new Source();
                                    image.source.width = resource.getIntrinsicWidth();
                                    image.source.height = resource.getIntrinsicHeight();
                                    post.data.preview.images = new ArrayList<>();
                                    post.data.preview.images.add(image);

                                    holder.mediaImage.getLayoutParams().height = (int) ((float) width / image.source.width * image.source.height);
                                }
                                return false;
                            }
                        })
                        .into(holder.mediaImage);
                if (imageDimensAvailable) {
                    Source source = post.data.preview.images.get(0).source;
                    holder.mediaImage.getLayoutParams().height = (int) ((float) width / source.width * source.height);
                }
                break;
            case TYPE_ALBUM:
                assert holder.mediaContainer != null;

                RecyclerView recyclerView = (RecyclerView) holder.mediaContainer;

                RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(context);
                recyclerView.setLayoutManager(layoutManager);

                final List<com.veyndan.redditclient.Image> images = new ArrayList<>();

                final AlbumAdapter albumAdapter = new AlbumAdapter(images, width);
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

                imgurService.album(post.data.url.split("/a/")[1])
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(basic -> {
                            images.addAll(basic.data.images);
                            albumAdapter.notifyDataSetChanged();
                        });
                break;
            case TYPE_TWEET:
                assert holder.mediaContainer != null;

                long tweetId = Long.parseLong(post.data.url.substring(post.data.url.indexOf("/status/") + "/status/".length()));
                TweetUtils.loadTweet(tweetId, new Callback<Tweet>() {
                    @Override
                    public void success(Result<Tweet> result) {
                        ((TweetView) holder.mediaContainer).setTweet(result.data);
                    }

                    @Override
                    public void failure(TwitterException exception) {
                        Log.e(TAG, "Load Tweet failure", exception);
                    }
                });
                break;
            case TYPE_LINK_IMAGE:
                assert holder.mediaImage != null;
                assert holder.mediaImageProgress != null;

                holder.mediaImageProgress.setVisibility(View.VISIBLE);

                Source source = post.data.preview.images.get(0).source;
                Glide.with(context)
                        .load(source.url)
                        .listener(new RequestListener<String, GlideDrawable>() {
                            @Override
                            public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
                                holder.mediaImageProgress.setVisibility(View.GONE);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                                holder.mediaImageProgress.setVisibility(View.GONE);
                                return false;
                            }
                        })
                        .into(holder.mediaImage);
            case TYPE_LINK:
                assert holder.mediaContainer != null;
                assert holder.mediaUrl != null;

                RxView.clicks(holder.mediaContainer)
                        .subscribe(aVoid -> {
                            Thing<Link> post1 = posts.get(holder.getAdapterPosition());
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(post1.data.url));
                            context.startActivity(intent);
                        });

                String urlHost;
                try {
                    urlHost = new URL(post.data.url).getHost();
                } catch (MalformedURLException e) {
                    Log.e(TAG, e.getMessage(), e);
                    urlHost = post.data.url;
                }

                holder.mediaUrl.setText(urlHost);
                break;
        }

        if ((viewType & TYPE_FLAIR) != 0) {
            assert holder.flairContainer != null;

            holder.flairContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(context);

            if (post.data.stickied) {
                TextView flairStickied = (TextView) inflater.inflate(R.layout.post_flair_stickied, holder.flairContainer, false);
                holder.flairContainer.addView(flairStickied);
            }

            if (post.data.over18) {
                TextView flairNsfw = (TextView) inflater.inflate(R.layout.post_flair_nsfw, holder.flairContainer, false);
                holder.flairContainer.addView(flairNsfw);
            }

            if (!TextUtils.isEmpty(post.data.linkFlairText)) {
                TextView flairLink = (TextView) inflater.inflate(R.layout.post_flair_link, holder.flairContainer, false);
                holder.flairContainer.addView(flairLink);

                flairLink.setText(post.data.linkFlairText);
            }

            if (post.data.gilded != 0) {
                TextView flairGilded = (TextView) inflater.inflate(R.layout.post_flair_gilded, holder.flairContainer, false);
                holder.flairContainer.addView(flairGilded);

                flairGilded.setText(String.valueOf(post.data.gilded));
            }
        }

        final String points = context.getResources().getQuantityString(R.plurals.points, post.data.score, post.data.score);
        final String comments = context.getResources().getQuantityString(R.plurals.comments, post.data.numComments, post.data.numComments);
        holder.score.setText(context.getString(R.string.score, points, comments));

        VoteDirection likes = post.data.getLikes();

        holder.upvote.setChecked(likes.equals(VoteDirection.UPVOTE));
        RxCompoundButton.checkedChanges(holder.upvote)
                .skip(1)
                .subscribe(isChecked -> {
                    // Ensure that downvote and upvote aren't checked at the same time.
                    if (isChecked) {
                        holder.downvote.setChecked(false);
                    }

                    Thing<Link> post1 = posts.get(holder.getAdapterPosition());
                    post1.data.setLikes(isChecked ? VoteDirection.UPVOTE : VoteDirection.UNVOTE);
                    if (!post1.data.archived) {
                        reddit.vote(isChecked ? VoteDirection.UPVOTE : VoteDirection.UNVOTE, post1.kind + "_" + post1.data.id)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe();

                        post1.data.score += isChecked ? 1 : -1;

                        final String points1 = context.getResources().getQuantityString(R.plurals.points, post1.data.score, post1.data.score);
                        final String comments1 = context.getResources().getQuantityString(R.plurals.comments, post1.data.numComments, post1.data.numComments);
                        holder.score.setText(context.getString(R.string.score, points1, comments1));
                    }
                });

        holder.downvote.setChecked(likes.equals(VoteDirection.DOWNVOTE));
        RxCompoundButton.checkedChanges(holder.downvote)
                .skip(1)
                .subscribe(isChecked -> {
                    // Ensure that downvote and upvote aren't checked at the same time.
                    if (isChecked) {
                        holder.upvote.setChecked(false);
                    }

                    Thing<Link> post1 = posts.get(holder.getAdapterPosition());
                    post1.data.setLikes(isChecked ? VoteDirection.DOWNVOTE : VoteDirection.UNVOTE);
                    if (!post1.data.archived) {
                        reddit.vote(isChecked ? VoteDirection.DOWNVOTE : VoteDirection.UNVOTE, post1.kind + "_" + post1.data.id)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe();

                        post1.data.score += isChecked ? -1 : 1;

                        final String points1 = context.getResources().getQuantityString(R.plurals.points, post1.data.score, post1.data.score);
                        final String comments1 = context.getResources().getQuantityString(R.plurals.comments, post1.data.numComments, post1.data.numComments);
                        holder.score.setText(context.getString(R.string.score, points1, comments1));
                    }
                });

        holder.save.setChecked(post.data.saved);
        RxCompoundButton.checkedChanges(holder.save)
                .skip(1)
                .subscribe(isChecked -> {
                    Thing<Link> post1 = posts.get(holder.getAdapterPosition());
                    post1.data.saved = isChecked;
                    if (isChecked) {
                        reddit.save("", post1.kind + "_" + post1.data.id)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe();
                    } else {
                        reddit.unsave(post1.kind + "_" + post1.data.id)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe();
                    }
                });

        final PopupMenu otherMenu = new PopupMenu(context, holder.other);
        otherMenu.getMenuInflater().inflate(R.menu.menu_post_other, otherMenu.getMenu());

        RxView.clicks(holder.other)
                .subscribe(aVoid -> otherMenu.show());

        RxPopupMenu.itemClicks(otherMenu)
                .subscribe(menuItem -> {
                    final int adapterPosition = holder.getAdapterPosition();

                    switch (menuItem.getItemId()) {
                        case R.id.action_post_hide:
                            final View.OnClickListener undoClickListener = view -> {
                                // If undo pressed, then don't follow through with request to hide
                                // the post.
                                posts.add(adapterPosition, post);
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
                                            reddit.hide(post.kind + "_" + post.data.id)
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
                            intent.putExtra("username", post.data.author);
                            context.startActivity(intent);
                            break;
                        case R.id.action_post_subreddit:
                            intent = new Intent(context.getApplicationContext(), MainActivity.class);
                            intent.putExtra("subreddit", post.data.subreddit);
                            context.startActivity(intent);
                            break;
                        case R.id.action_post_browser:
                            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(post.data.url));
                            context.startActivity(intent);
                            break;
                        case R.id.action_post_report:
                            break;
                    }
                });
    }

    @Override
    public int getItemViewType(int position) {
        Thing<Link> post = posts.get(position);

        int viewType;

        if (post.kind.equals("t3") && post.data.url.contains("imgur.com/") && !post.data.url.contains("/a/") && !post.data.url.contains("/gallery/") && !post.data.url.contains("i.imgur.com")) {
            post.data.url = post.data.url.replace("imgur.com", "i.imgur.com");
            if (!post.data.url.endsWith(".gifv")) {
                post.data.url += ".png";
            }
            post.data.setPostHint(PostHint.IMAGE);
        }

        if (post.data.isSelf) {
            viewType = TYPE_SELF;
        } else if (post.kind.equals("t3") && post.data.url.contains("twitter.com")) {
            viewType = TYPE_TWEET;
        } else if (post.kind.equals("t3") && post.data.getPostHint().equals(PostHint.IMAGE)) {
            viewType = TYPE_IMAGE;
        } else if (post.kind.equals("t3") && post.data.url.contains("/a/")) {
            viewType = TYPE_ALBUM;
        } else if (!post.data.preview.images.isEmpty()) {
            viewType = TYPE_LINK_IMAGE;
        } else {
            viewType = TYPE_LINK;
        }

        if (post.data.stickied || post.data.over18 || !TextUtils.isEmpty(post.data.linkFlairText)
                || post.data.gilded != 0) {
            viewType += TYPE_FLAIR;
        }

        return viewType;
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.post_title) TextView title;
        @BindView(R.id.post_subtitle) TextView subtitle;
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

        // Flair
        @Nullable @BindView(R.id.post_flair_container) ViewGroup flairContainer;

        public PostViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
