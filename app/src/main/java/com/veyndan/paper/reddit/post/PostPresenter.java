package com.veyndan.paper.reddit.post;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.veyndan.paper.reddit.Presenter;
import com.veyndan.paper.reddit.api.reddit.model.Listing;
import com.veyndan.paper.reddit.api.reddit.model.Thing;
import com.veyndan.paper.reddit.util.Node;

import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import retrofit2.Response;

public class PostPresenter implements Presenter<PostMvpView<Response<Thing<Listing>>>> {

    @Nullable private PostMvpView<Response<Thing<Listing>>> postMvpView;

    @Override
    public void attachView(@NonNull final PostMvpView<Response<Thing<Listing>>> view) {
        postMvpView = view;
    }

    @Override
    public void detachView() {
        postMvpView = null;
    }

    public void loadNode(@NonNull final Node<Response<Thing<Listing>>> node) {
        loadNodes(Collections.singletonList(node));
    }

    public void loadNodes(@NonNull final List<Node<Response<Thing<Listing>>>> nodes) {
        postMvpView.appendNodes(nodes);

        Observable.fromIterable(nodes)
                .subscribeOn(AndroidSchedulers.mainThread())
                .flatMap(node -> node.getTrigger()
                        .filter(Boolean::booleanValue)
                        .firstElement()
                        .flatMapObservable(aBoolean -> node.asObservable()))
                .observeOn(AndroidSchedulers.mainThread())
                .concatMap(node -> node.preOrderTraverse(0))
                .toList()
                .subscribe(nodes1 -> {
                    postMvpView.popNode();
                    loadNodes(nodes1);
                });
    }
}
