package com.veyndan.redditclient;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import rawjava.Reddit;
import rawjava.model.Link;
import rawjava.model.Listing;
import rawjava.model.Thing;
import rawjava.network.Credentials;
import rawjava.network.Sort;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Credentials credentials = Credentials.create(getResources().openRawResource(R.raw.credentials));
        Reddit reddit = new Reddit(credentials);

        final List<Thing<Link>> posts = new ArrayList<>();

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        final PostAdapter postAdapter = new PostAdapter(posts, reddit);
        recyclerView.setAdapter(postAdapter);

        reddit.subreddit("all", Sort.HOT)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Thing<Listing<Thing<Link>>>>() {
                    @Override
                    public void call(Thing<Listing<Thing<Link>>> post) {
                        posts.addAll(post.data.children);
                        postAdapter.notifyDataSetChanged();
                    }
                });
    }
}
