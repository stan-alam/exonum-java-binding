/* 
 * Copyright 2018 The Exonum Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exonum.binding.cryptocurrency.transactions;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.exonum.binding.crypto.PublicKey;
import com.exonum.binding.cryptocurrency.CryptocurrencySchema;
import com.exonum.binding.cryptocurrency.PredefinedOwnerKeys;
import com.exonum.binding.cryptocurrency.Wallet;
import com.exonum.binding.hash.HashCode;
import com.exonum.binding.messages.BinaryMessage;
import com.exonum.binding.messages.Transaction;
import com.exonum.binding.proxy.Cleaner;
import com.exonum.binding.proxy.CloseFailuresException;
import com.exonum.binding.storage.database.Database;
import com.exonum.binding.storage.database.Fork;
import com.exonum.binding.storage.database.MemoryDb;
import com.exonum.binding.storage.indices.MapIndex;
import com.exonum.binding.storage.indices.ProofMapIndexProxy;
import com.exonum.binding.util.LibraryLoader;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class TransferTxTest {

  static {
    LibraryLoader.load();
  }

  private static final PublicKey fromKey = PredefinedOwnerKeys.firstOwnerKey;

  private static final PublicKey toKey = PredefinedOwnerKeys.secondOwnerKey;

  @Test
  public void isValid() {
    long seed = 1L;
    long sum = 50L;

    TransferTx tx = new TransferTx(seed, fromKey, toKey, sum);

    assertTrue(tx.isValid());
  }

  @Test
  public void executeTransfer() throws CloseFailuresException {
    try (Database db = MemoryDb.newInstance();
         Cleaner cleaner = new Cleaner()) {
      Fork view = db.createFork(cleaner);
      // Create source and target wallets with the given initial balances
      long initialBalance = 100L;
      createWallet(view, fromKey, initialBalance);
      createWallet(view, toKey, initialBalance);

      // Create and execute the transaction
      long seed = 1L;
      long transferSum = 40L;
      TransferTx tx = new TransferTx(seed, fromKey, toKey, transferSum);
      tx.execute(view);

      // Check that wallets have correct balances
      CryptocurrencySchema schema = new CryptocurrencySchema(view);
      ProofMapIndexProxy<PublicKey, Wallet> wallets = schema.wallets();
      long expectedFromValue = initialBalance - transferSum;
      assertThat(wallets.get(fromKey).getBalance(), equalTo(expectedFromValue));
      long expectedToValue = initialBalance + transferSum;
      assertThat(wallets.get(toKey).getBalance(), equalTo(expectedToValue));
    }
  }

  @Test
  public void executeTransferToTheSameWallet() throws CloseFailuresException {
    try (Database db = MemoryDb.newInstance();
        Cleaner cleaner = new Cleaner()) {
      Fork view = db.createFork(cleaner);

      long initialBalance = 100L;
      createWallet(view, fromKey, initialBalance);

      // Create and execute the transaction
      long seed = 1L;
      long transferSum = 40L;
      TransferTx tx = new TransferTx(seed, fromKey, fromKey, transferSum);
      tx.execute(view);

      // Check that the balance of the wallet remains the same
      CryptocurrencySchema schema = new CryptocurrencySchema(view);
      ProofMapIndexProxy<PublicKey, Wallet> wallets = schema.wallets();
      assertThat(wallets.get(fromKey).getBalance(), equalTo(initialBalance));
    }
  }

  @Test
  public void executeNoSuchFromWallet() throws CloseFailuresException {
    try (Database db = MemoryDb.newInstance();
         Cleaner cleaner = new Cleaner()) {
      Fork view = db.createFork(cleaner);
      // Create source wallet with the given initial balance
      long initialBalance = 50L;
      createWallet(view, fromKey, initialBalance);

      long seed = 1L;
      long transferValue = 50L;
      TransferTx tx = new TransferTx(seed, fromKey, toKey, transferValue);
      // Execute the transaction that attempts to transfer to an unknown wallet
      tx.execute(view);

      // Check that balance of fromKey is unchanged
      CryptocurrencySchema schema = new CryptocurrencySchema(view);
      MapIndex<PublicKey, Wallet> wallets = schema.wallets();
      assertThat(wallets.get(fromKey).getBalance(), equalTo(initialBalance));
    }
  }

  @Test
  public void executeNoSuchToWallet() throws CloseFailuresException {
    try (Database db = MemoryDb.newInstance();
         Cleaner cleaner = new Cleaner()) {
      Fork view = db.createFork(cleaner);
      // Create and execute the transaction that attempts to transfer from unknown wallet
      long initialBalance = 100L;
      createWallet(view, toKey, initialBalance);
      long transferValue = 50L;
      long seed = 1L;
      TransferTx tx = new TransferTx(seed, fromKey, toKey, transferValue);
      tx.execute(view);

      // Check that balance of toKey is unchanged
      CryptocurrencySchema schema = new CryptocurrencySchema(view);
      MapIndex<PublicKey, Wallet> wallets = schema.wallets();
      assertThat(wallets.get(toKey).getBalance(), equalTo(initialBalance));
    }
  }

  @Test
  public void converterRoundtrip() {
    long seed = 0L;
    long sum = 50L;

    TransferTx tx = new TransferTx(seed, fromKey, toKey, sum);
    BinaryMessage message = TransferTx.converter().toMessage(tx);
    TransferTx txFromMessage = TransferTx.converter().fromMessage(message);

    assertThat(txFromMessage, equalTo(tx));
  }

  @Test
  public void info() {
    long seed = Long.MAX_VALUE - 1L;
    TransferTx tx = new TransferTx(seed, fromKey, toKey, 50L);

    String info = tx.info();

    // Check the transaction parameters in JSON
    Gson gson = CryptocurrencyTransactionGson.instance();

    Transaction txParameters = gson.fromJson(info, new TypeToken<TransferTx>() {}.getType());

    assertThat(txParameters, equalTo(tx));
  }

  @Test
  public void verifyEquals() {
    EqualsVerifier
        .forClass(TransferTx.class)
        .withPrefabValues(HashCode.class, HashCode.fromInt(1), HashCode.fromInt(2))
        .verify();
  }

  private void createWallet(Fork view, PublicKey publicKey, Long initialBalance) {
    CryptocurrencySchema schema = new CryptocurrencySchema(view);
    MapIndex<PublicKey, Wallet> wallets = schema.wallets();
    wallets.put(publicKey, new Wallet(initialBalance));
  }
}
