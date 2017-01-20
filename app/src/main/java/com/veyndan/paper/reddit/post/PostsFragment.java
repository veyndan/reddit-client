package com.veyndan.paper.reddit.post;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.hannesdorfmann.adaptercommands.AdapterCommandProcessor;
import com.hannesdorfmann.adaptercommands.command.AdapterCommand;
import com.hannesdorfmann.adaptercommands.command.ItemInsertedCommand;
import com.hannesdorfmann.adaptercommands.command.ItemRangeInsertedCommand;
import com.hannesdorfmann.adaptercommands.command.ItemRangeRemovedCommand;
import com.hannesdorfmann.adaptercommands.command.ItemRemovedCommand;
import com.jakewharton.rxbinding.support.v7.widget.RxRecyclerView;
import com.veyndan.paper.reddit.Config;
import com.veyndan.paper.reddit.ForestModel;
import com.veyndan.paper.reddit.R;
import com.veyndan.paper.reddit.api.reddit.Reddit;
import com.veyndan.paper.reddit.api.reddit.model.Listing;
import com.veyndan.paper.reddit.api.reddit.model.Thing;
import com.veyndan.paper.reddit.post.model.Progress;
import com.veyndan.paper.reddit.ui.recyclerview.SwipeItemTouchHelperCallback;
import com.veyndan.paper.reddit.ui.recyclerview.itemdecoration.MarginItemDecoration;
import com.veyndan.paper.reddit.ui.recyclerview.itemdecoration.TreeInsetItemDecoration;
import com.veyndan.paper.reddit.util.Node;
import com.veyndan.paper.reddit.util.list.command.AddAllCommand;
import com.veyndan.paper.reddit.util.list.command.AddCommand;
import com.veyndan.paper.reddit.util.list.command.ClearCommand;
import com.veyndan.paper.reddit.util.list.command.ListCommand;
import com.veyndan.paper.reddit.util.list.command.RemoveCommand;

import java.util.ArrayList;
import java.util.List;

import hu.akarnokd.rxjava.interop.RxJavaInterop;
import io.reactivex.Observable;
import io.reactivex.Single;
import retrofit2.Response;

public class PostsFragment extends Fragment implements PostMvpView<Response<Thing<Listing>>, ForestModel<Response<Thing<Listing>>>> {

    private final PostPresenter postPresenter = new PostPresenter();

    private RecyclerView recyclerView;

    // TODO This shouldn't be defined here as it should be in ForestModel.
    // TODO I will need to define a default ForestModel i.e. the trees in the model is empty.
    //  ??? Where do I define such a model.
    //  Pos Using startWith(default ForestModel)?
    private final List<Node<Response<Thing<Listing>>>> nodes = new ArrayList<>();

    private PostAdapter postAdapter;
    private AdapterCommandProcessor postAdapterCommandProcessor;

    private LinearLayoutManager layoutManager;

    // TODO Remove loadingPosts.
    //  ??? Find out how to define this differently as it is currently used in appendNode() and
    //      appendNodes(). This means that in the render() method there will have to be
    //      instanceof checks to see if the AdapterCommand is an insertion command and stop loading.
    //      In general the below is ugly and should be removed at some point anyway. Is there
    //      an RxJava operator which can do such a thing? Such as it listens to the trigger stream
    //      but if there is currently a stream being processed then ignore the trigger.
    private boolean loadingPosts;

    private Reddit reddit;

    @SuppressWarnings("RedundantNoArgConstructor")
    public PostsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        postPresenter.attachView(this);

        reddit = new Reddit(Config.REDDIT_CREDENTIALS);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    public void setRequest(final Single<Response<Thing<Listing>>> request) {
        if (nodes.size() > 0) {
            clearNodes();
        }
        postPresenter.loadNode(new Progress.Builder()
                .trigger(getTrigger())
                .request(request.toMaybe())
                .build());
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        recyclerView = (RecyclerView) inflater.inflate(R.layout.fragment_posts, container, false);

        layoutManager = new LinearLayoutManager(getActivity());
        postAdapter = new PostAdapter(getActivity(), nodes, reddit);
        postAdapterCommandProcessor = new AdapterCommandProcessor(postAdapter);

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addItemDecoration(new MarginItemDecoration(getActivity(), R.dimen.card_view_margin));
        recyclerView.addItemDecoration(new TreeInsetItemDecoration(getActivity(), R.dimen.post_child_inset_multiplier));
        recyclerView.setAdapter(postAdapter);

        final ItemTouchHelper.Callback swipeCallback = new SwipeItemTouchHelperCallback();
        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        return recyclerView;
    }

    @Override
    public void onDestroy() {
        postPresenter.detachView();
        super.onDestroy();
    }

    // TODO Here we would add the render method defined in MvpView.
    // TODO With the ForestModel we will replace the current immutable list of items in the adapter
    //      to a new list of immutable items.
    // TODO Update rules can be defined using http://hannesdorfmann.com/android/adapter-commands
    //  ??? Should the ForestModel define what was inserted and deleted as this feels like a
    //      thing that the View should deal with.

    @Override
    public void appendNode(final Node<Response<Thing<Listing>>> node) {
        render(new ForestModel.Builder<Response<Thing<Listing>>>()
                .previousForest(nodes)
                .adapterCommand(new ItemInsertedCommand(nodes.size()))
                .listCommand(new AddCommand<>(node))
                .build());
    }

    @Override
    public void appendNodes(final List<? extends Node<Response<Thing<Listing>>>> nodes) {
        render(new ForestModel.Builder<Response<Thing<Listing>>>()
                .previousForest(this.nodes)
                .adapterCommand(new ItemRangeInsertedCommand(this.nodes.size(), nodes.size()))
                .listCommand(new AddAllCommand<>(nodes))
                .build());
    }

    @Override
    public void popNode() {
        popNode(nodes.size() - 1);
    }

    @Override
    public void popNode(final int index) {
        render(new ForestModel.Builder<Response<Thing<Listing>>>()
                .previousForest(nodes)
                .adapterCommand(new ItemRemovedCommand(index))
                .listCommand(new RemoveCommand<>(index))
                .build());
    }

    @Override
    public void clearNodes() {
        render(new ForestModel.Builder<Response<Thing<Listing>>>()
                .previousForest(nodes)
                .adapterCommand(new ItemRangeRemovedCommand(0, nodes.size()))
                .listCommand(new ClearCommand<>())
                .build());
    }

    @Override
    public void render(final ForestModel<Response<Thing<Listing>>> forestModel) {
        for (final AdapterCommand adapterCommand : forestModel.getAdapterCommands()) {
            if (adapterCommand instanceof ItemRangeInsertedCommand || adapterCommand instanceof ItemInsertedCommand) {
                loadingPosts = false;
            }
        }

        for (final ListCommand<Node<Response<Thing<Listing>>>> collectionCommand : forestModel.getListCommands()) {
            collectionCommand.execute(forestModel.getPreviousForest());
        }

        nodes.clear();
        nodes.addAll(forestModel.getPreviousForest());
        postAdapterCommandProcessor.execute(forestModel.getAdapterCommands());
    }

    private Observable<Boolean> getTrigger() {
        return Observable.concat(getFirstPageTrigger(), getNextPageTrigger())
                .filter(Boolean::booleanValue)
                .filter(aBoolean -> !loadingPosts)
                .doOnNext(aBoolean -> loadingPosts = true);
    }

    private Observable<Boolean> getFirstPageTrigger() {
        return Observable.fromIterable(nodes)
                .count()
                .map(count -> count == 1)
                .toObservable();
    }

    private Observable<Boolean> getNextPageTrigger() {
        return RxJavaInterop.toV2Observable(
                RxRecyclerView.scrollEvents(recyclerView)
                        .filter(scrollEvent -> scrollEvent.dy() > 0) //check for scroll down
                        .map(scrollEvent -> {
                            final int visibleItemCount = recyclerView.getChildCount();
                            final int totalItemCount = layoutManager.getItemCount();
                            final int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                            return totalItemCount - visibleItemCount <= firstVisibleItem;
                        })
        );
    }
}
