package com.breadwallet.presenter.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.breadwallet.R;
import com.breadwallet.core.BRCoreAddress;
import com.breadwallet.fch.Base58;
import com.breadwallet.fch.DataCache;
import com.breadwallet.presenter.activities.util.BRActivity;
import com.breadwallet.tools.adapter.AddressListAdapter;
import com.breadwallet.tools.listeners.RecyclerItemClickListener;
import com.breadwallet.tools.manager.BRClipboardManager;
import com.breadwallet.tools.security.BRKeyStore;
import com.breadwallet.tools.util.BRConstants;
import com.breadwallet.wallet.WalletsMaster;
import com.breadwallet.wallet.abstracts.BaseWalletManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class AddressListActivity extends BRActivity {

    private RecyclerView mRecyclerView;
    private AddressListAdapter mAdapter;
    private List<String> mList;

    private String mAddress;
    private byte[] mRawPhrase;
    private boolean mDumping = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_list);
        mRecyclerView = findViewById(R.id.address_list);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new AddressListAdapter(this);
        mRecyclerView.setAdapter(mAdapter);
        mList = DataCache.getInstance().getAddressList();
        mAdapter.setData(mList);

        mRecyclerView.addOnItemTouchListener(new RecyclerItemClickListener(this, mRecyclerView, new RecyclerItemClickListener.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position, float x, float y) {
                mAddress = mList.get(position);
                showDialog();
            }

            @Override
            public void onLongItemClick(View view, int position) {
            }
        }));
        findViewById(R.id.back_icon).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    private void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_address);
        builder.setNegativeButton(R.string.copy_address, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                BRClipboardManager.putClipboard(AddressListActivity.this, mAddress);
                Toast.makeText(AddressListActivity.this, R.string.toast_copy_address, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setPositiveButton(R.string.dump_key, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mDumping = true;
                try {
                    mRawPhrase = BRKeyStore.getPhrase(AddressListActivity.this, BRConstants.SIGN_CODE);
                    dumpKey();
                } catch (UserNotAuthenticatedException e) {
                    Log.e("####", "AddressListActivity: WARNING! Authentication Loop bug");
                    return;
                }
            }
        });
        builder.create().show();
    }

    private byte[] hex2Bytes(String str) {
        int length = str.length();
        byte[] data = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4) + Character
                    .digit(str.charAt(i + 1), 16));
        }
        return data;
    }

    private String wif(String key) {
        byte[] pkBytes = hex2Bytes("80" + key + "01");
        MessageDigest sha = null;
        String res = "";
        try {
            sha = MessageDigest.getInstance("SHA-256");
            byte[] sha1 = sha.digest(pkBytes);
            sha.reset();
            byte[] sha2 = sha.digest(sha1);

            byte[] compressedKey = new byte[pkBytes.length + 4];
            for (int i = 0; i < pkBytes.length; i++) {
                compressedKey[i] = pkBytes[i];
            }
            for (int j = 0; j < 4; j++) {
                compressedKey[j + pkBytes.length] = sha2[j];
            }
            res = Base58.encode(compressedKey);
        } catch (NoSuchAlgorithmException e) {

        }
        return res;
    }

    private void dumpKey() {
        byte[] script = new BRCoreAddress(mAddress).getPubKeyScript();
        BaseWalletManager manager = WalletsMaster.getInstance().getCurrentWallet(this);
        String hex = manager.dumpPrivkey(script, mRawPhrase);
        String res = wif(hex);
        mDumping = false;
        BRClipboardManager.putClipboard(AddressListActivity.this, res);
        Toast.makeText(AddressListActivity.this, R.string.toast_copy_key, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mDumping) {
            return;
        }
        try {
            mRawPhrase = BRKeyStore.getPhrase(AddressListActivity.this, BRConstants.PAY_REQUEST_CODE);
            dumpKey();
        } catch (UserNotAuthenticatedException e) {
            Log.e("####", "onResume: WARNING! Authentication Loop bug");
            return;
        }
    }
}