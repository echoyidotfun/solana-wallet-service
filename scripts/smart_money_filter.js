// smart_money_activity_filter.js (Program ID Filter Only)

// --- 配置开始 ---

const BASE58_ALPHABET =
  "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

// 1. 目标 Program IDs
const PROGRAM_IDS = {
  JUPITER: "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4",
  OKX_DEX: "6m2CDdhRgxpH4WjvdzxAYbGxwdGUz5MziiL5jek2kBma",
  PUMP: "6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P",
  PUMP_AMM: "pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA",
  BOOP: "boop8hVGQGqehUK2iVEMEnMrL5RbjywRzHKBmBE7ry4",
  RAYDIUM: "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8",
  RAYDIUM_CAMM: "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK",
  RAYDIUM_CPMM: "CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C",
  RAYDIUM_LAUNCHPAD: "LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj",
  METEORA_POOLS: "Eo7WjKq67rjJQSZxS6z3YkapzY3eMj6Xy8X5EQVn5UaB",
};

// 2. 指令判别式 (Instruction Discriminators)
const IX_DISCRIMINATORS = {
  [PROGRAM_IDS.OKX_DEX]: {
    SWAP2: [65, 75, 63, 76, 235, 91, 91, 136], // swap2
  },
  [PROGRAM_IDS.JUPITER]: {
    ROUTE: [229, 23, 203, 151, 122, 227, 173, 42], // route
    SHARED_ACCOUNTS_EXACT_OUT_ROUTE: [176, 209, 105, 168, 154, 125, 69, 62], // shared_accounts_exact_out_route
    SHARED_ACCOUNTS_ROUTE: [193, 32, 155, 51, 65, 214, 156, 129], // shared_accounts_route
  },
  [PROGRAM_IDS.PUMP]: {
    BUY: [102, 6, 61, 18, 1, 218, 235, 234], // buy
    SELL: [51, 230, 133, 164, 1, 127, 131, 173], // sell
  },
  [PROGRAM_IDS.RAYDIUM]: {
    SWAP: [9, 235, 55, 209, 35, 32, 2, 0], // swap
  },
  [PROGRAM_IDS.PUMP_AMM]: {
    BUY: [102, 6, 61, 18, 1, 218, 235, 234], // buy
    SELL: [51, 230, 133, 164, 1, 127, 131, 173], // sell
  },
  [PROGRAM_IDS.RAYDIUM_CAMM]: {
    SWAPV2: [43, 4, 237, 11, 26, 201, 30, 98], // swapv2
  },
  [PROGRAM_IDS.RAYDIUM_CPMM]: {
    SWAP_BASE_INPUT: [143, 190, 90, 218, 196, 30, 51, 222], // swapBaseInput
    SWAP_BASE_OUTPUT: [55, 217, 98, 86, 163, 74, 180, 173], // swapBaseOutput
  },
  [PROGRAM_IDS.RAYDIUM_LAUNCHPAD]: {
    BUY_EXACT_IN: [250, 234, 13, 123, 213, 156, 19, 236], // buy_exact_in
    SELL_EXACT_IN: [149, 39, 222, 155, 211, 124, 152, 26], // sell_exact_in
  },
  [PROGRAM_IDS.METEORA_POOLS]: {
    SWAP: [248, 198, 158, 145, 225, 117, 135, 200], // swap
  },
  [PROGRAM_IDS.BOOP]: {
    SELL_TOKEN: [109, 61, 40, 187, 230, 176, 135, 174], // sell_token
    BUY_TOKEN: [138, 127, 14, 91, 38, 87, 115, 105], // buy_token
  },
};

// 3. 聪明钱地址列表
const TRACKED_ADDRESSES = new Set([
  "21Re7ghAGLCH1WPwygPJonkBSQL2AkUWeAhLcsbmPVk5",
  "2LNQo1MGb7MN9S9fQA2s9K4vi9orceJMXyjMdLMjZuK1",
  "2ttd8u5xKg5Qnhpm8RZaMAk1sXi5tgVeTo8Eq4K3A7AH",
  "3nanLHKo79m8YcwDxnzPQAemzrLQrMReFcVpata81yUQ",
  "4BdKaxN8G6ka4GYtQQWk4G4dZRUTX2vQH9GcXdBREFUk",
  "4x8f26yQhGCzC68uQ2iRYRbWe66Byf8bCiZss5NBq1xH",
  "55JgKgTce188T2GLwitmx1LVU7iQ6NGzVNkAbu51zMPV",
  "5gca64MnVt7DHmrwaBjtvVvuzo7zwQYMyQKJsqdEKaa3",
  "5urHgx7BYM3fh13a8v9tKC83dpJzy9oMFeMD1WtrVNaa",
  "67LPayhALpHduuFaYnyPJXFsXGjvEP27pbf4wsvuyXVK",
  "6CdcPWiWaueXC76Ne5DW7Y8MMGhJ76sJ4xvmwRn7Nx5",
  "6uGttQsT1ygU1kWo6inUnC6TLqdPXgDcTMHA99ijxd8N",
  "72e6QM7gn9MH5u1YpgQbduexm4WAon1cnsSXPqKnLQec",
  "7nozzaFyPvwAEvnRkPmkAAB4DrJrj2JSb7cmEsTdPV6W",
  "7oG4hDmFVVcNbco55efZogyUGr7ZtAtEJNKuxQBj6CmK",
  "8W5EuhHKqtQeioaBS8pHfBnyiE7CwoPSY9UDWFRuWc64",
  "9dgP6ciSqytSJrcJoqEk5RM2kzMRZeJNLkchhm1u7eaf",
  "9NL6thsiaoyDnm7XF8hEbMoqeG172WmG7iKYpygvjfgo",
  "As7HjL7dzzvbRbaD3WCun47robib2kmAKRXMvjHkSMB5",
  "BieeZkdnBAgNYknzo3RH2vku7FcPkFZMZmRJANh2TpW",
  "BMGZLEnNUCweeP7anae1YvLuihCH93gjL6ZcjGRPWyu1",
  "BPNUnorWNGLAhek7aK9Qf4hP4k2TRcWh2TMqwH9a3mXT",
  "C1yAWAqRYVFb9fbMXtsSa9Pf9PYt5YRmkPzpJGKpVcKN",
  "CcpShDGrXoJG3uLdc1D7tSCsJ9yHcqAExpsNZPxyhz6n",
  "CwN3agfJdQX2hwjmBsvBWy3HVHNLj9uaKbsX4vSoqY2M",
  "CWvdyvKHEu8Z6QqGraJT3sLPyp9bJfFhoXcxUYRKC8ou",
  "DfMxre4cKmvogbLrPigxmibVTTQDuzjdXojWzjCXXhzj",
  "DYAn4XpAkN5mhiXkRB7dGq4Jadnx6XYgu8L5b3WGhbrt",
  "DZjy7xGsBDpCj9gtVbLg1Ju1QrvHg9n9rwbx1EfRWdjN",
  "E2SRcmvvX71efevxnYJTcW9oggnprz7Xk2aSj3DV558L",
  "EZvetfxrsG5b6JjKgkUwkEePPvJqfM3SXvcVSbe3TV7V",
  "FKRtL4cSa8XxxcBUCka8UwVboheJCYaV1oyEPGa4eg3B",
  "FTFGKpZMiMuPdJVjjsVFReowfQc5b58gyRQEWXvP1zjt",
  "FZVdsFTRTWwZEy6XC5GCtHNWdcAwiQtAeJ2NSpH6cQNz",
  "G56xuvGRFKJUBdQbfCs3Wzah2p7tuvVJHHBatH3oTqyD",
  "Go29uBzT3xCmmWQEWvG7g2NvusksqVALyKEY3G56ue8V",
  "HAmFnVktrp7VRTh3bvkfcmxkTpH48GK3iv5VLYFpGwVv",
  "HTLEGhX1XEuTxByCwMeGvUvSYMG4xgbbdDpJhoKnfQNi",
  "HWUt5Z6gmEJXfGjUpxhFTWhrRL1CP4fcYyytyK3LKSSA",
  "J7LdaT9tCEruGnZWbujxGYvXtZ3VZZtReh7sGjcahRXg",
  "J865X6P4QDE7xp5JmkuPhyeRHZqddnVKR9pZiAJtZEAm",
  "ve94tFcRnLnwMGf7e5mUXMyAkzzcS4qkzSKZGsQV4kk",
]);

// 4. 其他过滤配置
const FILTER_CONFIG = {
  skipFailed: true, // 设置为 true 跳过失败的交易, false 则包含失败的
  enableTrackedAddressesFilter: true, // 是否只返回与聪明钱相关的交易
};

// --- 配置结束 ---

/**
 * Helper function to convert a byte array (instruction data) to a hex string.
 */
function dataToHex(dataArray) {
  if (!dataArray || !Array.isArray(dataArray) || dataArray.length === 0) {
    return "";
  }
  return dataArray.map((b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * Decodes a base58 string into a byte array.
 */
function decodeBase58(encoded) {
  if (typeof encoded !== "string") return [];
  const result = [];
  for (let i = 0; i < encoded.length; i++) {
    let carry = BASE58_ALPHABET.indexOf(encoded[i]);
    if (carry < 0) return []; // Invalid character
    for (let j = 0; j < result.length; j++) {
      carry += result[j] * 58;
      result[j] = carry & 0xff;
      carry >>= 8;
    }
    while (carry > 0) {
      result.push(carry & 0xff);
      carry >>= 8;
    }
  }
  // Add leading zeros
  for (let i = 0; i < encoded.length && encoded[i] === "1"; i++) {
    result.push(0);
  }
  return result.reverse();
}

/**
 * 检查交易是否涉及聪明钱地址
 */
function involvesTrackedAddresses(accountKeys) {
  if (!FILTER_CONFIG.enableTrackedAddressesFilter) return true; // 如果未启用聪明钱过滤，则所有交易都视为涉及

  for (const account of accountKeys) {
    if (account && account.pubkey && TRACKED_ADDRESSES.has(account.pubkey)) {
      return account.pubkey;
    }
  }
  return null;
}

/**
 * QuickNode Stream Function 入口点
 */
function main(stream) {
  try {
    const dataPayload = stream.data && stream.data[0] ? stream.data[0] : stream;

    if (
      !dataPayload ||
      !dataPayload.transactions ||
      !Array.isArray(dataPayload.transactions) ||
      dataPayload.transactions.length === 0
    ) {
      return null;
    }

    const blockTime = dataPayload.blockTime || null; // Use block-level timestamp if available
    const allMatchedTransactions = [];

    for (const tx of dataPayload.transactions) {
      const matchedTxData = analyzeAndFormatTransaction(tx, blockTime);
      if (matchedTxData) {
        allMatchedTransactions.push(matchedTxData);
      }
    }

    if (allMatchedTransactions.length === 0) {
      return null;
    }
    // Return structure matches what QuickNode expects for transaction streams
    return {
      blockTime: dataPayload.blockTime,
      matchedTransactions: allMatchedTransactions,
    };
  } catch (error) {
    console.error(
      "Error in QuickNode Stream Function:",
      error.message,
      error.stack
    );
    // Return error information in a way that QuickNode might expect or log
    return {
      error: `StreamError: ${error.message}`,
      stack: error.stack,
      matchedTransactions: [],
    };
  }
}

/**
 * 分析单笔交易是否涉及目标 Program ID 和指令判别式, 并格式化输出
 */
function analyzeAndFormatTransaction(tx, blockTime) {
  if (
    !tx ||
    !tx.transaction ||
    !tx.transaction.message ||
    !tx.transaction.signatures ||
    tx.transaction.signatures.length === 0
  ) {
    return null; // Invalid transaction structure
  }

  if (FILTER_CONFIG.skipFailed && tx.meta?.err !== null) {
    return null;
  }

  const topLevelAccountKeys = tx.transaction.message.accountKeys || [];
  let trackedAddress = null;

  // 检查交易是否涉及聪明钱地址
  if (
    FILTER_CONFIG.enableTrackedAddressesFilter &&
    !(trackedAddress = involvesTrackedAddresses(topLevelAccountKeys))
  ) {
    return null; // 如果不涉及聪明钱地址，则跳过此交易
  }

  const matchedInstructionsInfo = [];

  for (const ix of tx.transaction.message.instructions) {
    if (!ix || !ix.programId) continue;

    if (Object.values(PROGRAM_IDS).includes(ix.programId)) {
      const programDiscriminators = IX_DISCRIMINATORS[ix.programId];
      if (!programDiscriminators) continue;

      const decodedIxData = decodeBase58(ix.data);
      if (decodedIxData.length < 8) continue;

      const ixDiscriminator = decodedIxData.slice(0, 8);

      for (const [type, definedDiscriminator] of Object.entries(
        programDiscriminators
      )) {
        if (
          definedDiscriminator.length === 8 &&
          definedDiscriminator.every(
            (byte, index) => byte === ixDiscriminator[index]
          )
        ) {
          let friendlyProgramName = "UNKNOWN";
          if (ix.programId === PROGRAM_IDS.JUPITER)
            friendlyProgramName = "JUPITER";
          else if (ix.programId === PROGRAM_IDS.OKX_DEX)
            friendlyProgramName = "OKX_DEX";
          else if (ix.programId === PROGRAM_IDS.PUMP)
            friendlyProgramName = "PUMP";
          else if (ix.programId === PROGRAM_IDS.PUMP_AMM)
            friendlyProgramName = "PUMP_AMM";
          else if (ix.programId === PROGRAM_IDS.BOOP)
            friendlyProgramName = "BOOP";
          else if (ix.programId === PROGRAM_IDS.RAYDIUM)
            friendlyProgramName = "RAYDIUM";
          else if (ix.programId === PROGRAM_IDS.RAYDIUM_CAMM)
            friendlyProgramName = "RAYDIUM_CAMM";
          else if (ix.programId === PROGRAM_IDS.RAYDIUM_CPMM)
            friendlyProgramName = "RAYDIUM_CPMM";
          else if (ix.programId === PROGRAM_IDS.RAYDIUM_LAUNCHPAD)
            friendlyProgramName = "RAYDIUM_LAUNCHPAD";
          else if (ix.programId === PROGRAM_IDS.METEORA_POOLS)
            friendlyProgramName = "METEORA_POOLS";

          matchedInstructionsInfo.push({
            programId: ix.programId,
            instructionType: `${friendlyProgramName}_${type}`,
            rawData: ix.data, // Store original base58 data or hex? For now, original. dataToHex(decodedIxData) to store hex.
            decodedDataHex: dataToHex(decodedIxData), // Store decoded data as hex for easier debugging
            accounts: ix.accounts,
          });
        }
      }
    }
  }

  if (matchedInstructionsInfo.length === 0) {
    return null; // No relevant instructions found in this transaction
  }

  // 如果启用了聪明钱过滤，添加涉及的聪明钱地址列表
  const result = {
    signature: tx.transaction.signatures[0],
    slot: tx.slot,
    trackedAddress: trackedAddress,
    blockTime: blockTime || tx.blockTime || null,
    // logMessages: tx.meta?.logMessages || null,
    preBalances: tx.meta?.preBalances || null,
    postBalances: tx.meta?.postBalances || null,
    preTokenBalances: tx.meta?.preTokenBalances || [],
    postTokenBalances: tx.meta?.postTokenBalances || [],
    matchedInstructions: matchedInstructionsInfo,
  };

  return result;
}
