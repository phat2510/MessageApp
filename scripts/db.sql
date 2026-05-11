CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
                       user_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       name        VARCHAR(100) NOT NULL,
                       created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE conversations (
                               conversation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               type            VARCHAR(10) NOT NULL CHECK (type IN ('PRIVATE', 'GROUP')),
                               created_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE conversation_members (
                                      conversation_id UUID REFERENCES conversations(conversation_id),
                                      user_id         UUID REFERENCES users(user_id),
                                      joined_at       TIMESTAMP DEFAULT NOW(),
                                      PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE messages (
                          message_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          conversation_id UUID REFERENCES conversations(conversation_id),
                          sender_id       UUID REFERENCES users(user_id),
                          content         TEXT NOT NULL,
                          type            VARCHAR(10) NOT NULL CHECK (type IN ('TEXT', 'IMAGE', 'VIDEO')),
                          created_at      TIMESTAMP DEFAULT NOW()
);

-- Index để query tin nhắn theo conversation nhanh hơn
CREATE INDEX idx_messages_conversation ON messages(conversation_id, created_at DESC);