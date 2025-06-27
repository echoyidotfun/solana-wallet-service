-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Create tokens table
CREATE TABLE tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mint_address VARCHAR(64) UNIQUE NOT NULL,
    symbol VARCHAR(50),
    name VARCHAR(255),
    decimals INTEGER NOT NULL DEFAULT 9,
    logo_uri VARCHAR(500),
    description TEXT,
    website VARCHAR(500),
    twitter VARCHAR(500),
    telegram VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create token_market_data table
CREATE TABLE token_market_data (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id UUID NOT NULL REFERENCES tokens(id) ON DELETE CASCADE,
    price DECIMAL(20,10),
    price_usd DECIMAL(20,10),
    volume_24h DECIMAL(20,4),
    volume_change_24h DECIMAL(10,4),
    market_cap DECIMAL(20,4),
    market_cap_rank INTEGER,
    price_change_1h DECIMAL(10,4),
    price_change_24h DECIMAL(10,4),
    price_change_7d DECIMAL(10,4),
    circulating_supply DECIMAL(20,4),
    total_supply DECIMAL(20,4),
    max_supply DECIMAL(20,4),
    ath DECIMAL(20,10),
    atl DECIMAL(20,10),
    last_updated TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create token_trending_ranking table
CREATE TABLE token_trending_ranking (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id UUID NOT NULL REFERENCES tokens(id) ON DELETE CASCADE,
    rank INTEGER NOT NULL,
    category VARCHAR(50) NOT NULL,
    timeframe VARCHAR(10) NOT NULL,
    score DECIMAL(10,4),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(token_id, category, timeframe)
);

-- Create token_top_holders table
CREATE TABLE token_top_holders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id UUID NOT NULL REFERENCES tokens(id) ON DELETE CASCADE,
    holder_address VARCHAR(64) NOT NULL,
    balance DECIMAL(20,4),
    percentage DECIMAL(6,4),
    rank INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(token_id, holder_address)
);

-- Create token_transaction_stats table
CREATE TABLE token_transaction_stats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id UUID NOT NULL REFERENCES tokens(id) ON DELETE CASCADE,
    timeframe VARCHAR(10) NOT NULL,
    transaction_count INTEGER,
    buy_count INTEGER,
    sell_count INTEGER,
    unique_traders INTEGER,
    buy_volume DECIMAL(20,4),
    sell_volume DECIMAL(20,4),
    net_volume DECIMAL(20,4),
    average_trade_size DECIMAL(20,4),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(token_id, timeframe)
);

-- Create trade_rooms table
CREATE TABLE trade_rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id VARCHAR(20) UNIQUE NOT NULL,
    creator_address VARCHAR(64) NOT NULL,
    token_id UUID REFERENCES tokens(id) ON DELETE SET NULL,
    token_address VARCHAR(64),
    password VARCHAR(255),
    recycle_hours INTEGER NOT NULL DEFAULT 24,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    max_members INTEGER NOT NULL DEFAULT 100,
    current_members INTEGER NOT NULL DEFAULT 1,
    last_activity TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CHECK (status IN ('active', 'closed', 'expired'))
);

-- Create room_members table
CREATE TABLE room_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES trade_rooms(id) ON DELETE CASCADE,
    wallet_address VARCHAR(64) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_seen TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    is_online BOOLEAN DEFAULT FALSE,
    role VARCHAR(20) NOT NULL DEFAULT 'member',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(room_id, wallet_address),
    CHECK (role IN ('creator', 'member'))
);

-- Create shared_infos table
CREATE TABLE shared_infos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES trade_rooms(id) ON DELETE CASCADE,
    sharer_address VARCHAR(64) NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    is_sticky BOOLEAN DEFAULT FALSE,
    view_count INTEGER DEFAULT 0,
    like_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CHECK (type IN ('analysis', 'signal', 'news', 'discussion', 'alert'))
);

-- Create trade_events table
CREATE TABLE trade_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES trade_rooms(id) ON DELETE CASCADE,
    wallet_address VARCHAR(64) NOT NULL,
    token_address VARCHAR(64) NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    amount DECIMAL(20,8),
    price DECIMAL(20,10),
    value_usd DECIMAL(20,4),
    tx_signature VARCHAR(128),
    block_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CHECK (event_type IN ('buy', 'sell'))
);

-- Create traders table
CREATE TABLE traders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_address VARCHAR(64) UNIQUE NOT NULL,
    nickname VARCHAR(100),
    avatar VARCHAR(500),
    is_verified BOOLEAN DEFAULT FALSE,
    is_tracked BOOLEAN DEFAULT FALSE,
    total_trades INTEGER DEFAULT 0,
    win_rate DECIMAL(5,4) DEFAULT 0,
    total_pnl DECIMAL(20,4) DEFAULT 0,
    avg_hold_time INTEGER DEFAULT 0,
    last_active_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    reputation INTEGER DEFAULT 0,
    follower_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create smart_money_transactions table
CREATE TABLE smart_money_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    signature VARCHAR(128) UNIQUE NOT NULL,
    slot BIGINT NOT NULL,
    block_time TIMESTAMP WITH TIME ZONE,
    wallet_address VARCHAR(64) NOT NULL,
    token_address VARCHAR(64) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(20,8),
    price DECIMAL(20,10),
    value_usd DECIMAL(20,4),
    program_id VARCHAR(64),
    instruction_type VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'success',
    pre_balances JSONB,
    post_balances JSONB,
    pre_token_balances JSONB,
    post_token_balances JSONB,
    log_messages TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CHECK (transaction_type IN ('buy', 'sell', 'swap', 'transfer')),
    CHECK (status IN ('success', 'failed', 'pending'))
);

-- Create transaction_analysis table
CREATE TABLE transaction_analysis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES smart_money_transactions(id) ON DELETE CASCADE,
    analysis_type VARCHAR(50) NOT NULL,
    confidence DECIMAL(4,3),
    sentiment VARCHAR(20),
    signal_strength INTEGER CHECK (signal_strength >= 1 AND signal_strength <= 10),
    risk_level VARCHAR(20),
    summary TEXT,
    detailed_analysis TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create wallet_following table
CREATE TABLE wallet_following (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_address VARCHAR(64) NOT NULL,
    following_address VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(follower_address, following_address)
);

-- Create indexes for better performance
CREATE INDEX idx_tokens_mint_address ON tokens(mint_address);
CREATE INDEX idx_token_market_data_token_id ON token_market_data(token_id);
CREATE INDEX idx_token_trending_ranking_category ON token_trending_ranking(category, timeframe);
CREATE INDEX idx_trade_rooms_status ON trade_rooms(status);
CREATE INDEX idx_trade_rooms_creator ON trade_rooms(creator_address);
CREATE INDEX idx_room_members_wallet ON room_members(wallet_address);
CREATE INDEX idx_smart_money_wallet ON smart_money_transactions(wallet_address);
CREATE INDEX idx_smart_money_token ON smart_money_transactions(token_address);
CREATE INDEX idx_smart_money_block_time ON smart_money_transactions(block_time);
CREATE INDEX idx_trade_events_room ON trade_events(room_id);
CREATE INDEX idx_shared_infos_room ON shared_infos(room_id);

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Add updated_at triggers to all tables
CREATE TRIGGER update_tokens_updated_at BEFORE UPDATE ON tokens FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_token_market_data_updated_at BEFORE UPDATE ON token_market_data FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_token_trending_ranking_updated_at BEFORE UPDATE ON token_trending_ranking FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_token_top_holders_updated_at BEFORE UPDATE ON token_top_holders FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_token_transaction_stats_updated_at BEFORE UPDATE ON token_transaction_stats FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_trade_rooms_updated_at BEFORE UPDATE ON trade_rooms FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_room_members_updated_at BEFORE UPDATE ON room_members FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_shared_infos_updated_at BEFORE UPDATE ON shared_infos FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_traders_updated_at BEFORE UPDATE ON traders FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_smart_money_transactions_updated_at BEFORE UPDATE ON smart_money_transactions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_transaction_analysis_updated_at BEFORE UPDATE ON transaction_analysis FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();