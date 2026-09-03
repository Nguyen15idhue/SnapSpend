-- SnapSpend PostgreSQL schema. The ASP.NET API can also create the schema automatically on first run.
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(120) NOT NULL UNIQUE,
    username VARCHAR(40) NOT NULL UNIQUE,
    password_hash VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS categories (
    key VARCHAR(50) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    emoji VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS expenses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount BIGINT NOT NULL CHECK (amount > 0),
    category VARCHAR(50) NOT NULL DEFAULT 'other',
    image_url TEXT,
    note TEXT,
    expense_date DATE NOT NULL,
    ai_confidence DOUBLE PRECISION,
    category_source VARCHAR(20) NOT NULL DEFAULT 'user',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_expenses_user_date ON expenses(user_id, expense_date);

CREATE TABLE IF NOT EXISTS friendships (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    friend_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_friendship_pair UNIQUE(user_id, friend_id),
    CONSTRAINT ck_friendship_not_self CHECK(user_id <> friend_id)
);

CREATE TABLE IF NOT EXISTS expense_shares (
    id BIGSERIAL PRIMARY KEY,
    expense_id BIGINT NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_share UNIQUE(expense_id, receiver_id)
);

INSERT INTO categories(key, name, emoji) VALUES
('food','Ăn uống','🍜'),
('shopping','Shopping','🛍️'),
('transport','Đi lại','🛵'),
('entertainment','Giải trí','🎬'),
('housing','Nhà ở','🏠'),
('health','Sức khỏe','💊'),
('education','Học tập','📚'),
('bills','Hóa đơn','🧾'),
('other','Khác','•')
ON CONFLICT (key) DO NOTHING;
