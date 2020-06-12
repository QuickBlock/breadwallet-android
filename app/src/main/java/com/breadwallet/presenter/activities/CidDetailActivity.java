package com.breadwallet.presenter.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.breadwallet.R;
import com.breadwallet.fch.Cid;
import com.breadwallet.fch.DataCache;
import com.breadwallet.fch.TxHistoryTask;
import com.breadwallet.presenter.activities.util.BRActivity;
import com.breadwallet.presenter.entities.TxUiHolder;
import com.breadwallet.tools.animation.UiUtils;
import com.breadwallet.tools.util.Utils;
import com.breadwallet.ui.wallet.TransactionListAdapter;
import com.breadwallet.wallet.WalletsMaster;
import com.breadwallet.wallet.abstracts.BaseWalletManager;
import com.breadwallet.wallet.wallets.bitcoin.WalletFchManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CidDetailActivity extends BRActivity {

    public static final String ACTIVITY_ACTION = "ACTIVITY_ACTION";
    public static final String ACTION_HISTORY = "ACTION_HISTORY";

    private BaseWalletManager mWalletManager;
    private DataCache mDataCache;
    private Cid mCid;

    private TextView mName;
    private TextView mBalance;
    private TextView mSend, mReceive, mSign;
    private RecyclerView mRecyclerView;
    private TransactionListAdapter mAdapter;

    private BroadcastReceiver mReceiver;
    private List<TxUiHolder> mTemp = new ArrayList<TxUiHolder>();
    private int mPage = 1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cid_detail);
        mName = findViewById(R.id.detail_cid);
        mBalance = findViewById(R.id.detail_balance);
        mSend = findViewById(R.id.detail_send);
        mReceive = findViewById(R.id.detail_receive);
        mSign = findViewById(R.id.detail_sign);
        mRecyclerView = findViewById(R.id.rv_history);

        mWalletManager = WalletsMaster.getInstance().getCurrentWallet(this);
        mDataCache = mDataCache.getInstance();

        int position = getIntent().getIntExtra("cid_position", 0);
        mCid = mDataCache.getCidList().get(position);
        mName.setText(mCid.getName());

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(ACTION_HISTORY)) {
                    String data = intent.getStringExtra("history");
                    refreshHistory(data);
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTIVITY_ACTION);
        filter.addAction(ACTION_HISTORY);
        registerReceiver(mReceiver, filter);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new TransactionListAdapter(this, null, new TransactionListAdapter.OnItemClickListener() {
            @Override
            public void onItemClicked(TxUiHolder item) {

            }
        });

        mRecyclerView.setAdapter(mAdapter);
        mSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(CidDetailActivity.this, SendActivity.class);
                i.putExtra("address", mCid.getAddress());
                startActivity(i);
            }
        });
        mReceive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UiUtils.showCidReceiveFragment(CidDetailActivity.this, mCid.getAddress());
            }
        });
        mSign.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(CidDetailActivity.this, SignActivity.class);
                i.putExtra("address", mCid.getAddress());
                startActivity(i);
            }
        });
        findViewById(R.id.close_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        new TxHistoryTask(this, mCid.getAddress(), mPage).execute();
    }

    private void refreshHistory(String data) {
        List<String> hashs = new ArrayList<String>();
        try {
            JSONArray arr = new JSONArray(data);
            for (int i = 0; i < arr.length(); ++i) {
                JSONObject obj = new JSONObject(arr.get(i).toString());
                int status = obj.getInt("status");
                if (status == 0) {
                    continue;
                }
                String txid = obj.getString("txid");
                txid = Utils.reverse(txid);
                if (!hashs.contains(txid)) {
                    hashs.add(txid);
                }
            }
            parseHistory(arr);
        } catch (Exception e) {

        }

        List<TxUiHolder> list = new ArrayList<TxUiHolder>();
        if (mWalletManager.getTxUiHolders(getApplication()) == null) {
            mAdapter.setItems(mTemp);
            mAdapter.notifyDataSetChanged();
            return;
        }

        for (TxUiHolder tx : mWalletManager.getTxUiHolders(getApplication())) {
            if (tx.getTo().equalsIgnoreCase(mCid.getAddress())) {
                list.add(tx);
            } else {
                for (String h : hashs) {
                    if (h.equalsIgnoreCase(Utils.bytesToHex(tx.getTxHash()))) {
                        list.add(tx);
                        break;
                    }
                }
            }
        }
        mAdapter.setItems(list);
        mAdapter.notifyDataSetChanged();
    }

    private void parseHistory(JSONArray arr) throws Exception {
        for (int i = 0; i < arr.length(); ++i) {
            JSONObject obj = new JSONObject(arr.get(i).toString());
            String height = obj.getString("height");
            int status = obj.getInt("status");
            String time = obj.getString("time");
            String txid = obj.getString("txid");
            String value = obj.getString("value");

            TxUiHolder tx = new TxUiHolder();
            tx.setBlockHeight(Integer.parseInt(height));
            tx.setReceived(status == 0);
            tx.setTimeStamp(Long.parseLong(time));
            tx.setTxHash(txid.getBytes());
            tx.setAmount(new BigDecimal(value).multiply(WalletFchManager.ONE_FCH_BD));
            tx.setFrom(mCid.getAddress());
            tx.setTo(mCid.getAddress());
            tx.setValid(true);
            tx.setErrored(false);
            mTemp.add(tx);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTemp.clear();
        if (mDataCache.getBalance().containsKey(mCid.getAddress())) {
            BigDecimal b = new BigDecimal(mDataCache.getBalance().get(mCid.getAddress())).divide(WalletFchManager.ONE_FCH_BD);
            mBalance.setText(b.toString());
        } else {
            mBalance.setText("0");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }
}
