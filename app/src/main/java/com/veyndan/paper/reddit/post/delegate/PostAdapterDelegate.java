package com.veyndan.paper.reddit.post.delegate;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsServiceConnection;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import com.hannesdorfmann.adapterdelegates3.AdapterDelegate;
import com.jakewharton.rxbinding.support.design.widget.RxSnackbar;
import com.jakewharton.rxbinding.view.RxView;
import com.jakewharton.rxbinding.widget.RxCompoundButton;
import com.jakewharton.rxbinding.widget.RxPopupMenu;
import com.veyndan.paper.reddit.MainActivity;
import com.veyndan.paper.reddit.R;
import com.veyndan.paper.reddit.api.reddit.Reddit;
import com.veyndan.paper.reddit.api.reddit.model.Listing;
import com.veyndan.paper.reddit.api.reddit.model.Thing;
import com.veyndan.paper.reddit.api.reddit.network.VoteDirection;
import com.veyndan.paper.reddit.databinding.PostItemBinding;
import com.veyndan.paper.reddit.post.Flair;
import com.veyndan.paper.reddit.post.PostAdapter;
import com.veyndan.paper.reddit.post.media.PostMediaAdapter;
import com.veyndan.paper.reddit.post.model.Post;
import com.veyndan.paper.reddit.ui.recyclerview.Swipeable;
import com.veyndan.paper.reddit.util.Node;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindColor;
import butterknife.BindDrawable;
import butterknife.BindString;
import butterknife.ButterKnife;
import io.reactivex.Maybe;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Response;

public class PostAdapterDelegate extends AdapterDelegate<List<Node<Response<Thing<Listing>>>>> {

    @NonNull private static final String CUSTOM_TAB_PACKAGE_NAME = "com.android.chrome";

    @BindColor(R.color.post_flair_stickied) int flairStickiedColor;
    @BindColor(R.color.post_flair_nsfw) int flairNsfwColor;
    @BindColor(R.color.post_flair_link) int flairLinkColor;
    @BindColor(R.color.post_flair_gilded) int flairGildedColor;

    @Nullable @BindDrawable(R.drawable.ic_star_white_12sp) Drawable flairGildedIcon;

    @Nullable @BindString(R.string.post_stickied) String flairStickiedText;
    @Nullable @BindString(R.string.post_nsfw) String flairNsfwText;
    @Nullable @BindString(R.string.score_hidden) String scoreHiddenText;

    @NonNull private final PostAdapter adapter;
    @NonNull private final Activity activity;
    @NonNull private final Reddit reddit;

    @NonNull private final CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
    @NonNull private final CustomTabsIntent customTabsIntent = builder.build();
    @NonNull private Maybe<CustomTabsClient> customTabsClient = Maybe.empty();

    public PostAdapterDelegate(@NonNull final PostAdapter adapter, @NonNull final Activity activity,
                               @NonNull final Reddit reddit) {
        this.adapter = adapter;
        this.activity = activity;
        this.reddit = reddit;

        CustomTabsClient.bindCustomTabsService(activity, CUSTOM_TAB_PACKAGE_NAME, new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(final ComponentName name, @Nullable final CustomTabsClient client) {
                // customTabsClient is now valid.
                customTabsClient = client == null ? Maybe.empty() : Maybe.just(client);
                customTabsClient.blockingGet().warmup(0);
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                // customTabsClient is no longer valid. This also invalidates sessions.
                customTabsClient = Maybe.empty();
            }
        });
    }

    @Override
    public boolean isForViewType(@NonNull final List<Node<Response<Thing<Listing>>>> nodes,
                                 final int position) {
        return nodes.get(position) instanceof Post;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent) {
        ButterKnife.bind(this, parent);
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final PostItemBinding binding = PostItemBinding.inflate(inflater, parent, false);
        return new PostViewHolder(binding, adapter, reddit);
    }

    @Override
    protected void onBindViewHolder(@NonNull final List<Node<Response<Thing<Listing>>>> nodes,
                                    final int position,
                                    @NonNull final RecyclerView.ViewHolder holder,
                                    @NonNull final List<Object> payloads) {
        final Context context = holder.itemView.getContext();
        final PostViewHolder postHolder = (PostViewHolder) holder;
        final Post post = (Post) nodes.get(position);

        bindHeader(post, postHolder);
        bindMedia(post, postHolder);
        bindPoints(context, post, postHolder);
        bindUpvoteAction(context, post, postHolder);
        bindDownvoteAction(context, post, postHolder);
        bindSaveAction(post, postHolder);
        bindCommentsAction(context, nodes, post, postHolder);

        final PopupMenu otherMenu = new PopupMenu(context, postHolder.binding.postOther);
        otherMenu.getMenuInflater().inflate(R.menu.menu_post_other, otherMenu.getMenu());

        RxView.clicks(postHolder.binding.postOther)
                .subscribe(aVoid -> otherMenu.show());

        RxPopupMenu.itemClicks(otherMenu)
                .subscribe(menuItem -> {
                    switch (menuItem.getItemId()) {
                        case R.id.action_post_share:
                            bindShareAction(context, post);
                            break;
                        case R.id.action_post_browser:
                            bindBrowserAction(context, post);
                            break;
                        case R.id.action_post_report:
                            break;
                    }
                });
    }

    private void bindHeader(@NonNull final Post post, @NonNull final PostViewHolder holder) {
        final List<Flair> flairs = new ArrayList<>();

        if (post.isStickied()) {
            flairs.add(new Flair.Builder(flairStickiedColor)
                    .text(flairStickiedText)
                    .build());
        }

        if (post.isNsfw()) {
            flairs.add(new Flair.Builder(flairNsfwColor)
                    .text(flairNsfwText)
                    .build());
        }

        if (post.getLinkFlair().count().blockingGet() == 1L) {
            flairs.add(new Flair.Builder(flairLinkColor)
                    .text(post.getLinkFlair().blockingGet())
                    .build());
        }

        if (post.isGilded()) {
            flairs.add(new Flair.Builder(flairGildedColor)
                    .text(String.valueOf(post.getGildedCount()))
                    .icon(flairGildedIcon)
                    .build());
        }

        holder.binding.postHeader.setHeader(post.getLinkTitle() == null ? Maybe.empty() : Maybe.just(post.getLinkTitle()), post.getAuthor(), post.getDisplayAge(),
                post.getSubreddit(), flairs);
    }

    private void bindMedia(@NonNull final Post post, @NonNull final PostViewHolder holder) {
        final PostMediaAdapter postMediaAdapter = new PostMediaAdapter(
                activity, customTabsClient, customTabsIntent, post, post.getMedias());
        holder.binding.postMediaView.setAdapter(postMediaAdapter);
    }

    private void bindPoints(@NonNull final Context context, @NonNull final Post post,
                            @NonNull final PostViewHolder holder) {
        final String points = post.getDisplayPoints(context, scoreHiddenText);
        holder.binding.postScore.setText(points);
    }

    private void bindUpvoteAction(@NonNull final Context context, @NonNull final Post post,
                                  @NonNull final PostViewHolder holder) {
        final VoteDirection likes = post.getLikes();
        holder.binding.postUpvoteNew.setChecked(likes == VoteDirection.UPVOTE);
        RxCompoundButton.checkedChanges(holder.binding.postUpvoteNew)
                // checkedChanges emits the checked state on subscription. As the voted state of
                // the Reddit post is the same as the checked state of the button initially,
                // skipping the initial emission means no unnecessary network requests occur.
                .skip(1)
                .subscribe(isChecked -> {
                    post.setLikes(isChecked ? VoteDirection.UPVOTE : VoteDirection.UNVOTE);
                    if (!post.isArchived()) {
                        reddit.vote(isChecked ? VoteDirection.UPVOTE : VoteDirection.UNVOTE, post.getFullname())
                                .subscribeOn(Schedulers.io())
                                .subscribe();

                        post.setPoints(post.getPoints() + (isChecked ? 1 : -1));

                        final String points1 = post.getDisplayPoints(context, scoreHiddenText);
                        holder.binding.postScore.setText(points1);
                    }
                });
    }

    private void bindDownvoteAction(@NonNull final Context context, @NonNull final Post post,
                                    @NonNull final PostViewHolder holder) {
        final VoteDirection likes = post.getLikes();
        holder.binding.postDownvoteNew.setChecked(likes == VoteDirection.DOWNVOTE);
        RxCompoundButton.checkedChanges(holder.binding.postDownvoteNew)
                // checkedChanges emits the checked state on subscription. As the voted state of
                // the Reddit post is the same as the checked state of the button initially,
                // skipping the initial emission means no unnecessary network requests occur.
                .skip(1)
                .subscribe(isChecked -> {
                    post.setLikes(isChecked ? VoteDirection.DOWNVOTE : VoteDirection.UNVOTE);
                    if (!post.isArchived()) {
                        reddit.vote(isChecked ? VoteDirection.DOWNVOTE : VoteDirection.UNVOTE, post.getFullname())
                                .subscribeOn(Schedulers.io())
                                .subscribe();

                        post.setPoints(post.getPoints() + (isChecked ? -1 : 1));

                        final String points1 = post.getDisplayPoints(context, scoreHiddenText);
                        holder.binding.postScore.setText(points1);
                    }
                });
    }

    private void bindSaveAction(@NonNull final Post post, @NonNull final PostViewHolder holder) {
        holder.binding.postSave.setChecked(post.isSaved());
        RxView.clicks(holder.binding.postSave)
                .subscribe(aVoid -> {
                    holder.binding.postSave.toggle();
                    final boolean isChecked = holder.binding.postSave.isChecked();

                    post.setSaved(isChecked);
                    if (isChecked) {
                        reddit.save("", post.getFullname())
                                .subscribeOn(Schedulers.io())
                                .subscribe();
                    } else {
                        reddit.unsave(post.getFullname())
                                .subscribeOn(Schedulers.io())
                                .subscribe();
                    }
                });
    }

    private void bindCommentsAction(@NonNull final Context context, @NonNull final List<Node<Response<Thing<Listing>>>> nodes,
                                    @NonNull final Post post, @NonNull final PostViewHolder holder) {
        RxView.clicks(holder.binding.postComments)
                .map(aVoid -> {
                    holder.binding.postComments.toggle();
                    return holder.binding.postComments.isChecked();
                })
                .subscribe(displayDescendants -> {
                    post.setDescendantsVisible(!displayDescendants);
                    if (post.isComment()) {
                        if (displayDescendants) {
                            int i;
                            for (i = holder.getAdapterPosition() + 1; i < nodes.size() && nodes.get(i).getDepth() > post.getDepth(); i++)
                                ;

                            nodes.subList(holder.getAdapterPosition() + 1, i).clear();
                            adapter.notifyItemRangeRemoved(holder.getAdapterPosition() + 1, i - (holder.getAdapterPosition() + 1));

                            holder.binding.postCommentCount.setVisibility(View.VISIBLE);
                            holder.binding.postCommentCount.setText(String.valueOf(i - (holder.getAdapterPosition() + 1)));
                        } else {
                            post.preOrderTraverse(post.getDepth())
                                    .skip(1)
                                    .toList()
                                    .subscribe(children -> {
                                        nodes.addAll(holder.getAdapterPosition() + 1, children);
                                        adapter.notifyItemRangeInserted(holder.getAdapterPosition() + 1, children.size());
                                    });

                            holder.binding.postCommentCount.setVisibility(View.INVISIBLE);
                        }
                    } else {
                        final Intent commentsIntent = new Intent(context, MainActivity.class);
                        commentsIntent.putExtra(Reddit.Filter.NODE_DEPTH, 0);
                        commentsIntent.putExtra(Reddit.Filter.COMMENTS_SUBREDDIT, post.getSubreddit());
                        commentsIntent.putExtra(Reddit.Filter.COMMENTS_ARTICLE, post.getArticle());
                        context.startActivity(commentsIntent);
                    }
                });

        if (post.isInternalNode() && !post.isDescendantsVisible()) {
            holder.binding.postCommentCount.setVisibility(View.VISIBLE);
            final String commentCount = post.getDisplayDescendants();
            holder.binding.postCommentCount.setText(commentCount);
        } else {
            holder.binding.postCommentCount.setVisibility(View.INVISIBLE);
        }
    }

    private static void bindShareAction(@NonNull final Context context, @NonNull final Post post) {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, post.getPermalink());
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    private static void bindBrowserAction(@NonNull final Context context, @NonNull final Post post) {
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(post.getLinkUrl()));
        context.startActivity(intent);
    }

    static class PostViewHolder extends RecyclerView.ViewHolder implements Swipeable {

        @NonNull private final PostItemBinding binding;
        @NonNull private final PostAdapter adapter;
        @NonNull private final Reddit reddit;

        PostViewHolder(@NonNull final PostItemBinding binding, @NonNull final PostAdapter adapter, @NonNull final Reddit reddit) {
            super(binding.getRoot());

            this.binding = binding;
            this.adapter = adapter;
            this.reddit = reddit;
        }

        @Override
        public boolean swipeable() {
            final int position = getAdapterPosition();
            final Post post = (Post) adapter.getItems().get(position);
            return post.isHideable();
        }

        @Override
        public void onSwipe() {
            final int position = getAdapterPosition();
            final Node<Response<Thing<Listing>>> node = adapter.getItems().get(position);
            final Post post = (Post) node;

            final View.OnClickListener undoClickListener = view -> {
                // If undo pressed, then don't follow through with request to hide
                // the post.
                adapter.getItems().add(position, node);
                adapter.notifyItemInserted(position);
            };

            final Snackbar snackbar = Snackbar.make(itemView, R.string.notify_post_hidden, Snackbar.LENGTH_LONG)
                    .setAction(R.string.notify_post_hidden_undo, undoClickListener);

            RxSnackbar.dismisses(snackbar)
                    // If undo pressed, don't hide post.
                    .takeFirst(event -> event != Snackbar.Callback.DISMISS_EVENT_ACTION)
                    .subscribe(event -> {
                        // Chance to undo post hiding has gone, so follow through with
                        // hiding network request.
                        reddit.hide(post.getFullname())
                                .subscribeOn(Schedulers.io())
                                .subscribe();
                    });

            snackbar.show();

            // Hide post from list, but make no network request yet. Outcome of the
            // user's interaction with the snackbar handling will determine this.
            adapter.getItems().remove(position);
            adapter.notifyItemRemoved(position);
        }
    }
}
