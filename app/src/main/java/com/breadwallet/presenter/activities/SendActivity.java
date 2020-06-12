package com.breadwallet.presenter.activities;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.breadwallet.R;
import com.breadwallet.core.BRCoreAddress;
import com.breadwallet.core.BRCoreTransaction;
import com.breadwallet.core.BRCoreTransactionInput;
import com.breadwallet.core.BRCoreTransactionOutput;
import com.breadwallet.fch.CidSpinnerAdapter;
import com.breadwallet.fch.DataCache;
import com.breadwallet.fch.SpUtil;
import com.breadwallet.fch.Utxo;
import com.breadwallet.presenter.activities.util.BRActivity;
import com.breadwallet.presenter.customviews.BRDialogView;
import com.breadwallet.presenter.entities.CryptoRequest;
import com.breadwallet.tools.animation.BRDialog;
import com.breadwallet.tools.animation.UiUtils;
import com.breadwallet.tools.manager.BRClipboardManager;
import com.breadwallet.tools.manager.SendManager;
import com.breadwallet.tools.threads.executor.BRExecutor;
import com.breadwallet.tools.util.Utils;
import com.breadwallet.wallet.WalletsMaster;
import com.breadwallet.wallet.abstracts.BaseWalletManager;
import com.breadwallet.wallet.wallets.CryptoTransaction;
import com.breadwallet.wallet.wallets.bitcoin.WalletFchManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SendActivity extends BRActivity {

    private BaseWalletManager mWalletManager;
    private DataCache mDataCache;

    private Spinner mSpinner;
    private TextView mTvBalance;
    private TextView mTvPaste;
    private TextView mSend;
    private EditText mEtAddress, mEtAmount, mEtMemo;

    private CidSpinnerAdapter mAdapter;
    private String mAddress, mTarget;
    private long mTotal, mFee, mCharge;
    private List<Utxo> mUtxos = new ArrayList<Utxo>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);
        mSpinner = findViewById(R.id.send_spinner);
        mTvBalance = findViewById(R.id.send_balance);
        mTvPaste = findViewById(R.id.send_paste);
        mSend = findViewById(R.id.send_send);
        mEtAddress = findViewById(R.id.send_address);
        mEtAmount = findViewById(R.id.send_amount);
        mEtMemo = findViewById(R.id.send_memo);

        mWalletManager = WalletsMaster.getInstance().getCurrentWallet(this);
        mDataCache = mDataCache.getInstance();

        List<String> addresses = mDataCache.getAddressList();
        mAdapter = new CidSpinnerAdapter(this, addresses);
        mSpinner.setAdapter(mAdapter);
        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long l) {
                mAddress = addresses.get(pos);
                if (mDataCache.getBalance().containsKey(mAddress)) {
                    long balance = mDataCache.getBalance().get(mAddress);
                    BigDecimal bd = new BigDecimal(balance).divide(WalletFchManager.ONE_FCH_BD);
                    mTvBalance.setText(String.format(getString(R.string.balance_format), bd.doubleValue()));
                } else {
                    mTvBalance.setText(String.format(getString(R.string.balance_format), 0.0));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        String a = getIntent().getStringExtra("address");
        for (int i = 0; i < addresses.size(); ++i) {
            if (a.equalsIgnoreCase(addresses.get(i))) {
                mSpinner.setSelection(i);
                break;
            }
        }

        mTvPaste.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mTarget = BRClipboardManager.getClipboard(SendActivity.this);
                mEtAddress.setText(mTarget);
            }
        });

        mSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mTarget = mEtAddress.getText().toString();
                String amount = mEtAmount.getText().toString();
                if (mTarget.isEmpty() || amount.isEmpty()) {
                    return;
                }

                if (mWalletManager.isAddressValid(mTarget)) {
                    if (mWalletManager.containsAddress(mTarget)) {
                        Toast.makeText(SendActivity.this, R.string.Send_containsAddress, Toast.LENGTH_LONG).show();
                    } else {
                        BigDecimal bd = new BigDecimal(amount).multiply(WalletFchManager.ONE_FCH_BD);
                        buildTx(bd.longValue());
                    }
                } else {
                    BRDialog.showCustomDialog(SendActivity.this, "", getResources().getString(R.string.Send_invalidAddressTitle),
                            getString(R.string.AccessibilityLabels_close), null, new BRDialogView.BROnClickListener() {
                                @Override
                                public void onClick(BRDialogView brDialogView) {
                                    brDialogView.dismiss();
                                }
                            }, null, null, 0);
                }
            }
        });

        findViewById(R.id.back_icon).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    private boolean prepareUtxo(long amount) {
        List<Utxo> list = mDataCache.getUtxoList();
        mTotal = 0;
        if (mEtMemo.getText().toString().trim().isEmpty()) {
            mFee = 500;
        } else {
            mFee = 1000;
        }
        mUtxos.clear();
        for (Utxo u : list) {
            if (u.getAddress().equalsIgnoreCase(mAddress)) {
                mTotal += u.getAmount();
                mFee += 500;
                mUtxos.add(u);
            }
        }
        return mTotal >= (mFee + amount);
    }

    private void buildTx(long amount) {
        Map<String, Long> map = mDataCache.getBalance();
        if (!map.containsKey(mAddress) || map.get(mAddress) < amount) {
            Toast.makeText(SendActivity.this, R.string.toast_balance, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!prepareUtxo(amount)) {
            Toast.makeText(SendActivity.this, R.string.toast_balance, Toast.LENGTH_SHORT).show();
            return;
        }
        createCid(amount);
    }

    private void createCid(long amount) {
        BRCoreTransaction tx = new BRCoreTransaction();
        byte[] empty = new byte[]{};
        long sequence = 4294967295L;

        BRCoreAddress inAddress = new BRCoreAddress(mAddress);
        byte[] inScript = inAddress.getPubKeyScript();
        for (Utxo u : mUtxos) {
            String s = Utils.reverse(u.getTxid());
            byte[] hash = Utils.hexToBytes(s);
            BRCoreTransactionInput in = new BRCoreTransactionInput(hash, u.getVout(), u.getAmount(), inScript, empty, empty, sequence);
            tx.addInput(in);
        }

        BRCoreAddress targetAddress = new BRCoreAddress(mTarget);
        byte[] targetScript = targetAddress.getPubKeyScript();
        BRCoreTransactionOutput out = new BRCoreTransactionOutput(amount, targetScript);
        tx.addOutput(out);

        mCharge = mTotal - mFee - amount;
        if (mFee > WalletFchManager.MAX_FEE) {
            BRDialog.showSimpleDialog(SendActivity.this, "Failed", "Too Many Fee");
            return;
        }
        if (mCharge > WalletFchManager.DUST) {
            BRCoreTransactionOutput charge = new BRCoreTransactionOutput(mCharge, inScript);
            tx.addOutput(charge);
        }

        String memo = mEtMemo.getText().toString().trim();
        if (!memo.isEmpty()) {
            String hexData = stringToHex(memo);
            int len = hexData.length() / 2;
            if (len < 16) {
                hexData = "6a0" + Integer.toHexString(len) + hexData;
            } else {
                hexData = "6a" + Integer.toHexString(len) + hexData;
            }
            BRCoreTransactionOutput dataOut = new BRCoreTransactionOutput(0, Utils.hexToBytes(hexData));
            tx.addOutput(dataOut);
        }

        CryptoTransaction transaction = new CryptoTransaction(tx);
        final CryptoRequest item = new CryptoRequest.Builder().setAddress(mTarget).setAmount(new BigDecimal(amount)).build();
        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(() -> {
            SendManager.sendTransaction2(this, item, transaction, mWalletManager, (txHash, succeeded) -> {
                if (!Utils.isNullOrEmpty(txHash) && succeeded) {
                    UiUtils.showBreadSignal(this, getString(R.string.Alerts_sendSuccess),
                            getString(R.string.Alerts_sendSuccessSubheader), R.drawable.ic_check_mark_white, () -> UiUtils.killAllFragments(this));
                    Log.e("####", "txHash = " + txHash);
                    updateUtxo(txHash);
                } else {
                    UiUtils.showBreadSignal(this, getString(R.string.Alert_error),
                            getString(R.string.Alerts_sendFailure), R.drawable.ic_error_outline_black_24dp, () -> UiUtils.killAllFragments(this));
                }

            });
        });
    }

    private void updateUtxo(String txid) {
        List<String> txs = mDataCache.getSpendTxid();
        List<Utxo> pending = mDataCache.getPendingList();
        List<Utxo> utxos = mDataCache.getUtxoList();
        Map<String, Long> map = mDataCache.getBalance();
        long balance = mDataCache.getTotalBalance();

        for (Utxo u : mUtxos) {
            String s = u.getTxid() + u.getVout();
            txs.add(s);

            if (pending.contains(u)) {
                pending.remove(u);
            }

            if (utxos.contains(u)) {
                utxos.remove(u);
            }
        }
        mDataCache.setSpendTxid(txs);
        SpUtil.putTxid(this, txs);

        balance -= mTotal;
        if (mCharge > WalletFchManager.DUST) {
            Utxo charge = new Utxo(txid, mAddress, mCharge, 1);
            pending.add(charge);
            map.put(mAddress, mCharge);
            balance += mCharge;
            utxos.add(charge);
            BigDecimal bd = new BigDecimal(mCharge + "").divide(WalletFchManager.ONE_FCH_BD);
            mTvBalance.setText(String.format(getString(R.string.balance_format), bd.doubleValue()));
        } else {
            map.put(mAddress, 0l);
            mTvBalance.setText(R.string.balance_format_zero);
        }
        mDataCache.setPendingList(pending);
        SpUtil.putPending(this, pending);

        mDataCache.setBalance(map);
        mDataCache.setTotalBalance(balance);
        mDataCache.setUtxoList(utxos);
    }

    private String stringToHex(String s) {
        String str = "";
        for (int i = 0; i < s.length(); i++) {
            int ch = s.charAt(i);
            String s4 = Integer.toHexString(ch);
            str = str + s4;
        }
        return str;
    }

}
