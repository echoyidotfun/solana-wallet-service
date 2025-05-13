const BASE58_ALPHABET =
  "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
const PUMP_FUN_PROGRAM_ID = "6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P";
const PUMP_FUN_CREATE_IX_DISCRIMINATOR = [24, 30, 200, 40, 5, 28, 7, 119];
const FILTER_CONFIG = {
  programIds: [PUMP_FUN_PROGRAM_ID],
  skipFailed: true,
  instructionDiscriminators: [PUMP_FUN_CREATE_IX_DISCRIMINATOR],
};
const ACCOUNTS_TO_INCLUDE = [
  {
    name: "mint",
    index: 0,
  },
];

function main(stream) {
  try {
    const data = stream.data[0];
    if (!data?.transactions?.length) {
      return { error: "Invalid or missing stream" };
    }

    const matchedTransactions = data.transactions
      .filter(matchesFilter)
      .map((tx) => formatTransaction(tx, data));

    if (matchedTransactions.length === 0) {
      return null;
    }
    return { matchedTransactions };
  } catch (error) {
    console.error("Error in main function:", error);
    return { error: error.message, stack: error.stack };
  }
}

function matchesFilter(tx) {
  if (FILTER_CONFIG.skipFailed && tx.meta?.err !== null) {
    return false;
  }

  const programIds = new Set(
    tx.transaction.message.instructions.map((ix) => ix.programId)
  );
  if (!FILTER_CONFIG.programIds.some((id) => programIds.has(id))) {
    return false;
  }

  return tx.transaction.message.instructions.some(
    matchesInstructionDiscriminator
  );
}

function matchesInstructionDiscriminator(ix) {
  if (!ix?.data) return false;
  const decodedData = decodeBase58(ix.data);
  return FILTER_CONFIG.instructionDiscriminators.some(
    (discriminator) =>
      discriminator.length === 8 &&
      discriminator.every((byte, index) => byte === decodedData[index])
  );
}

function decodeBase58(encoded) {
  if (typeof encoded !== "string") return [];
  const result = [];
  for (let i = 0; i < encoded.length; i++) {
    let carry = BASE58_ALPHABET.indexOf(encoded[i]);
    if (carry < 0) return []; // Invalid character, return empty array
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

function formatTransaction(tx, stream) {
  const matchingInstruction = tx.transaction.message.instructions.find(
    matchesInstructionDiscriminator
  );
  const includedAccounts = ACCOUNTS_TO_INCLUDE.reduce(
    (acc, { name, index }) => {
      acc[name] = matchingInstruction.accounts[index];
      return acc;
    },
    {}
  );

  return {
    signature: tx.transaction.signatures[0],
    blockTime: stream.blockTime,
    accounts: includedAccounts,
  };
}
