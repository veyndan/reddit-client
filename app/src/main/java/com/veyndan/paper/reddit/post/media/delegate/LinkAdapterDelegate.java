package com.veyndan.paper.reddit.post.media.delegate;

import android.app.Activity;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsSession;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.hannesdorfmann.adapterdelegates3.AbsListItemAdapterDelegate;
import com.jakewharton.rxbinding.view.RxView;
import com.veyndan.paper.reddit.databinding.PostMediaLinkBinding;
import com.veyndan.paper.reddit.post.media.model.Link;
import com.veyndan.paper.reddit.post.model.Post;

import java.util.List;

import io.reactivex.Maybe;

public class LinkAdapterDelegate
        extends AbsListItemAdapterDelegate<Link, Object, LinkAdapterDelegate.LinkViewHolder> {

    @NonNull private final Activity activity;
    @NonNull private final Maybe<CustomTabsClient> customTabsClient;
    @NonNull private final CustomTabsIntent customTabsIntent;
    @NonNull private final Post post;

    public LinkAdapterDelegate(@NonNull final Activity activity, @NonNull final Maybe<CustomTabsClient> customTabsClient,
                               @NonNull final CustomTabsIntent customTabsIntent, @NonNull final Post post) {
        this.activity = activity;
        this.customTabsClient = customTabsClient;
        this.customTabsIntent = customTabsIntent;
        this.post = post;
    }

    @Override
    protected boolean isForViewType(@NonNull final Object item, @NonNull final List<Object> items,
                                    final int position) {
        return item instanceof Link;
    }

    @NonNull
    @Override
    public LinkViewHolder onCreateViewHolder(@NonNull final ViewGroup parent) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final PostMediaLinkBinding binding = PostMediaLinkBinding.inflate(inflater, parent, false);
        return new LinkViewHolder(binding);
    }

    @Override
    protected void onBindViewHolder(@NonNull final Link link,
                                    @NonNull final LinkViewHolder holder,
                                    @NonNull final List<Object> payloads) {
        if (customTabsClient.count().blockingGet() == 1L) {
            final CustomTabsSession session = customTabsClient.blockingGet().newSession(null);

            session.mayLaunchUrl(Uri.parse(post.getLinkUrl()), null, null);
        }

        RxView.clicks(holder.binding.postMediaUrl)
                .subscribe(aVoid -> {
                    customTabsIntent.launchUrl(activity, Uri.parse(post.getLinkUrl()));
                });

        holder.binding.postMediaUrl.setText(link.getDomain());
    }

    static class LinkViewHolder extends RecyclerView.ViewHolder {

        @NonNull private final PostMediaLinkBinding binding;

        public LinkViewHolder(@NonNull final PostMediaLinkBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
        }
    }
}
