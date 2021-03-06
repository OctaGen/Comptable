package com.example.ismailamrani.comptable.ui.fragments;


import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.example.ismailamrani.comptable.R;
import com.example.ismailamrani.comptable.adapters.OrdersAdapter;
import com.example.ismailamrani.comptable.models.Order;
import com.example.ismailamrani.comptable.utils.decorations.SpacesItemDecoration;
import com.example.ismailamrani.comptable.utils.http.RequestListener;
import com.example.ismailamrani.comptable.utils.parsing.ListComparison;
import com.example.ismailamrani.comptable.utils.parsing.Orders;
import com.example.ismailamrani.comptable.utils.http.PhpAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * A simple {@link Fragment} subclass.
 */
public class OrdersListFragment extends Fragment {

    protected List<Order> mOrders;
    protected OrdersAdapter ordersAdapter;
    private OrderListFragListener listener;

    private String currentOrderType;

    /**
     * The orders' list.
     */
    @Bind(R.id.recyclerView)
    protected RecyclerView recyclerView;

    @Bind(R.id.emptyMessageLabel)
    protected TextView emptyMessageLabel;

    @Bind(R.id.progressBar)
    protected ProgressBar progressBar;

    /**
     * The view to be displayed in case a network error occur.
     */
    @Bind(R.id.errorLayout)
    protected RelativeLayout errorLayout;

    /**
     * The view to be displayed in case there were no orders to show.
     */
    @Bind(R.id.emptyLayout)
    protected RelativeLayout emptyView;

    @Bind(R.id.swipeRefreshLayout)
    protected SwipeRefreshLayout swipeRefreshLayout;

    public OrdersListFragment() {
        // Required empty public constructor
    }

    public static OrdersListFragment newInstance(String orderType) {
        OrdersListFragment fragment = new OrdersListFragment();
        Bundle args = new Bundle();

        args.putString("orderType", orderType);

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_orders_list, container, false);
        ButterKnife.bind(this, view);

        setupCurrentOrderType();
        setupSwipeRefresh();
        setupRecyclerView();

        String emptyText = getActivity().getString(R.string.no_orders_to_show);

        // Specify the message of the empty view
        emptyMessageLabel.setText(emptyText);

        refresh();

        return view;
    }

    private void setupCurrentOrderType() {
        Bundle args = getArguments();
        if (args != null)
            currentOrderType = args.getString("orderType");
    }

    private void setupRecyclerView() {
        mOrders = new ArrayList<>();

        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(
                new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        recyclerView.addItemDecoration(new SpacesItemDecoration(4));
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });
        swipeRefreshLayout.setColorSchemeResources(
                R.color.swipeRefresh1,
                R.color.swipeRefresh2,
                R.color.swipeRefresh3,
                R.color.swipeRefresh4
        );
    }

    public void refresh() {
        if (!swipeRefreshLayout.isRefreshing())
            swipeRefreshLayout.setRefreshing(true);

        Stream.of(emptyView, errorLayout)
                .forEach(new Consumer<RelativeLayout>() {
                    @Override
                    public void accept(RelativeLayout v) {
                        v.setVisibility(View.GONE);
                    }
                });

        if (listener != null) {
            String url = Orders.SALE.equals(currentOrderType) ?
                    PhpAPI.getSaleOrder : PhpAPI.getPurchaseOrder;

            listener.fetchOrders(url,
                    new RequestListener() {
                        @Override
                        public void onRequestSucceeded(JSONObject response) {
                            try {
                                List<Order> orders = Order.parseOrders(
                                        response.getJSONArray("orders"));

                                if (ListComparison.areEqual(mOrders, orders))
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            handleDataChange();
                                        }
                                    });
                                else {
                                    mOrders = orders;
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            onDataChanged();
                                        }
                                    });
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onRequestFailed(int status, JSONObject response) {

                        }

                        @Override
                        public void onNetworkError() {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    errorLayout.setVisibility(View.VISIBLE);
                                    progressBar.setVisibility(View.GONE);
                                    recyclerView.setVisibility(View.GONE);
                                    stopSwipeRefresh();
                                }
                            });
                        }

                        private void onDataChanged() {
                            populateRecyclerView();
                            handleDataChange();
                        }

                        private void handleDataChange() {
                            toggleRecyclerviewState();
                            progressBar.setVisibility(View.GONE);
                            stopSwipeRefresh();
                        }
                    });
        }
    }

    /**
     * Toggles the visibility of the RecyclerView & the empty view associated with it.
     */
    private void toggleRecyclerviewState() {
        emptyView.setVisibility(mOrders.size() == 0 ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(mOrders.size() == 0 ? View.GONE : View.VISIBLE);
        errorLayout.setVisibility(View.GONE);
    }

    private void populateRecyclerView() {
        if (ordersAdapter == null) {
            ordersAdapter = new OrdersAdapter(getActivity(), mOrders);
            ordersAdapter.setOrdersListener(new OrdersAdapter.OrdersListener() {
                @Override
                public void onOrderSelected(Order order) {
                    if (listener != null)
                        listener.onOrderItemPressed(order);
                }
            });
            recyclerView.setAdapter(ordersAdapter);
        } else
            ordersAdapter.animateTo(mOrders);
    }

    private void stopSwipeRefresh() {
        if (swipeRefreshLayout.isRefreshing())
            swipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (OrderListFragListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OrderListFragListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public interface OrderListFragListener {
        void fetchOrders(String url, RequestListener listener);

        void onOrderItemPressed(Order order);
    }
}
